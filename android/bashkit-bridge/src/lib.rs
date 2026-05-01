use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::{Duration, Instant};

use async_trait::async_trait;
use bashkit::{Bash, Builtin, BuiltinContext, ExecResult};
use jni::objects::{GlobalRef, JObject, JObjectArray, JString, JValue};
use jni::sys::{jlong, jstring};
use jni::{JNIEnv, JavaVM};
use serde::{Deserialize, Serialize};

const WORKSPACE_MOUNT: &str = "/work";
const MAX_NATIVE_OUTPUT_CHARS: usize = 100_000;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ShellOutput {
    #[serde(rename = "exitCode")]
    pub exit_code: i32,
    pub stdout: String,
    pub stderr: String,
    #[serde(rename = "durationMs")]
    pub duration_ms: Option<u64>,
    pub error: Option<String>,
    #[serde(rename = "stdoutTruncated")]
    pub stdout_truncated: bool,
    #[serde(rename = "stderrTruncated")]
    pub stderr_truncated: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ClauneJsOutput {
    #[serde(rename = "exitCode")]
    pub exit_code: i32,
    pub stdout: String,
    pub stderr: String,
}

pub trait ClauneJsCallback: Send + Sync {
    fn run(&self, script_path: &str, argv: &[String], stdin: &str) -> ClauneJsOutput;
}

pub fn execute_blocking(
    workspace_root: impl AsRef<Path>,
    command: &str,
    timeout_ms: Option<u64>,
    callback: Option<Arc<dyn ClauneJsCallback>>,
) -> ShellOutput {
    let started = Instant::now();
    let runtime = match tokio::runtime::Builder::new_current_thread()
        .enable_time()
        .build()
    {
        Ok(runtime) => runtime,
        Err(error) => {
            return ShellOutput {
                exit_code: 1,
                stdout: String::new(),
                stderr: error.to_string(),
                duration_ms: Some(started.elapsed().as_millis() as u64),
                error: Some("failed to initialize Bashkit runtime".to_string()),
                stdout_truncated: false,
                stderr_truncated: false,
            };
        }
    };

    runtime.block_on(execute_async(
        workspace_root.as_ref().to_path_buf(),
        command.to_string(),
        timeout_ms,
        callback,
        started,
    ))
}

async fn execute_async(
    workspace_root: PathBuf,
    command: String,
    timeout_ms: Option<u64>,
    callback: Option<Arc<dyn ClauneJsCallback>>,
    started: Instant,
) -> ShellOutput {
    let execution = async {
        let mut bash = Bash::builder()
            .mount_real_readwrite_at(workspace_root, WORKSPACE_MOUNT)
            .cwd(WORKSPACE_MOUNT)
            .env("HOME", WORKSPACE_MOUNT)
            .env("PWD", WORKSPACE_MOUNT)
            .env("CLAUNE_WORKSPACE", WORKSPACE_MOUNT)
            .username("claune")
            .hostname("android")
            .builtin("claune-js", Box::new(ClauneJsBuiltin { callback }))
            .build();

        bash.exec(&command).await
    };

    let result = match timeout_ms.filter(|value| *value > 0) {
        Some(ms) => match tokio::time::timeout(Duration::from_millis(ms), execution).await {
            Ok(result) => result,
            Err(_) => {
                return ShellOutput {
                    exit_code: 124,
                    stdout: String::new(),
                    stderr: format!("command timed out after {ms}ms\n"),
                    duration_ms: Some(started.elapsed().as_millis() as u64),
                    error: Some("timeout".to_string()),
                    stdout_truncated: false,
                    stderr_truncated: false,
                };
            }
        },
        None => execution.await,
    };

    match result {
        Ok(result) => shell_output(
            result.exit_code,
            result.stdout,
            result.stderr,
            Some(started.elapsed().as_millis() as u64),
            None,
        ),
        Err(error) => ShellOutput {
            exit_code: 1,
            stdout: String::new(),
            stderr: format!("{error}\n"),
            duration_ms: Some(started.elapsed().as_millis() as u64),
            error: Some(error.to_string()),
            stdout_truncated: false,
            stderr_truncated: false,
        },
    }
}

fn shell_output(
    exit_code: i32,
    stdout: String,
    stderr: String,
    duration_ms: Option<u64>,
    error: Option<String>,
) -> ShellOutput {
    let (stdout, stdout_truncated) = cap_output(stdout);
    let (stderr, stderr_truncated) = cap_output(stderr);
    ShellOutput {
        exit_code,
        stdout,
        stderr,
        duration_ms,
        error,
        stdout_truncated,
        stderr_truncated,
    }
}

fn cap_output(value: String) -> (String, bool) {
    if value.len() <= MAX_NATIVE_OUTPUT_CHARS {
        return (value, false);
    }
    let mut start = value.len() - MAX_NATIVE_OUTPUT_CHARS;
    while !value.is_char_boundary(start) {
        start += 1;
    }
    (
        format!(
            "[output capped by native bridge to last {MAX_NATIVE_OUTPUT_CHARS} bytes]\n{}",
            &value[start..]
        ),
        true,
    )
}

struct ClauneJsBuiltin {
    callback: Option<Arc<dyn ClauneJsCallback>>,
}

#[async_trait]
impl Builtin for ClauneJsBuiltin {
    async fn execute(&self, ctx: BuiltinContext<'_>) -> bashkit::Result<ExecResult> {
        let Some(callback) = &self.callback else {
            return Ok(ExecResult::err(
                "claune-js is not configured for this shell\n",
                127,
            ));
        };
        let Some(script_path) = ctx.args.first() else {
            return Ok(ExecResult::err(
                "usage: claune-js <script-path|-> [args...]\n",
                2,
            ));
        };

        let argv = ctx.args.iter().skip(1).cloned().collect::<Vec<_>>();
        let stdin = ctx.stdin.unwrap_or("");
        let output = callback.run(script_path, &argv, stdin);
        Ok(ExecResult {
            stdout: output.stdout,
            stderr: output.stderr,
            exit_code: output.exit_code,
            ..Default::default()
        })
    }
}

struct JniClauneJsCallback {
    vm: JavaVM,
    callback: GlobalRef,
}

impl ClauneJsCallback for JniClauneJsCallback {
    fn run(&self, script_path: &str, argv: &[String], stdin: &str) -> ClauneJsOutput {
        match self.run_inner(script_path, argv, stdin) {
            Ok(output) => output,
            Err(error) => ClauneJsOutput {
                exit_code: 1,
                stdout: String::new(),
                stderr: format!("claune-js JNI callback failed: {error}\n"),
            },
        }
    }
}

impl JniClauneJsCallback {
    fn run_inner(
        &self,
        script_path: &str,
        argv: &[String],
        stdin: &str,
    ) -> Result<ClauneJsOutput, String> {
        let mut env = self
            .vm
            .attach_current_thread()
            .map_err(|error| error.to_string())?;
        let script_path = env
            .new_string(script_path)
            .map_err(|error| error.to_string())?;
        let stdin = env.new_string(stdin).map_err(|error| error.to_string())?;
        let argv_array = new_string_array(&mut env, argv)?;
        let result = env
            .call_method(
                self.callback.as_obj(),
                "run",
                "(Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                &[
                    JValue::Object(&JObject::from(script_path)),
                    JValue::Object(&JObject::from(argv_array)),
                    JValue::Object(&JObject::from(stdin)),
                ],
            )
            .map_err(|error| error.to_string())?
            .l()
            .map_err(|error| error.to_string())?;
        let result_json: String = env
            .get_string(&JString::from(result))
            .map_err(|error| error.to_string())?
            .into();

        serde_json::from_str(&result_json).map_err(|error| error.to_string())
    }
}

fn new_string_array<'local>(
    env: &mut JNIEnv<'local>,
    values: &[String],
) -> Result<JObjectArray<'local>, String> {
    let string_class = env
        .find_class("java/lang/String")
        .map_err(|error| error.to_string())?;
    let array = env
        .new_object_array(values.len() as i32, string_class, JObject::null())
        .map_err(|error| error.to_string())?;
    for (index, value) in values.iter().enumerate() {
        let value = env.new_string(value).map_err(|error| error.to_string())?;
        env.set_object_array_element(&array, index as i32, value)
            .map_err(|error| error.to_string())?;
    }
    Ok(array)
}

#[no_mangle]
pub extern "system" fn Java_com_divyanshgolyan_claune_android_shell_BashkitWorkspaceShell_nativeExecute(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    workspace_root: JString<'_>,
    command: JString<'_>,
    timeout_ms: jlong,
    callback: JObject<'_>,
) -> jstring {
    let output = native_execute_inner(&mut env, workspace_root, command, timeout_ms, callback);
    let output_json = serde_json::to_string(&output).unwrap_or_else(|error| {
        format!(
            r#"{{"exitCode":1,"stdout":"","stderr":"failed to encode output: {error}\n","durationMs":null,"error":"serialization"}}"#
        )
    });
    match env.new_string(output_json) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

fn native_execute_inner(
    env: &mut JNIEnv<'_>,
    workspace_root: JString<'_>,
    command: JString<'_>,
    timeout_ms: jlong,
    callback: JObject<'_>,
) -> ShellOutput {
    let workspace_root = match env.get_string(&workspace_root) {
        Ok(value) => value.to_string_lossy().into_owned(),
        Err(error) => {
            return ShellOutput {
                exit_code: 1,
                stdout: String::new(),
                stderr: format!("invalid workspace root: {error}\n"),
                duration_ms: None,
                error: Some("invalid workspace root".to_string()),
                stdout_truncated: false,
                stderr_truncated: false,
            };
        }
    };
    let command = match env.get_string(&command) {
        Ok(value) => value.to_string_lossy().into_owned(),
        Err(error) => {
            return ShellOutput {
                exit_code: 1,
                stdout: String::new(),
                stderr: format!("invalid command: {error}\n"),
                duration_ms: None,
                error: Some("invalid command".to_string()),
                stdout_truncated: false,
                stderr_truncated: false,
            };
        }
    };
    let callback = if callback.is_null() {
        None
    } else {
        match env
            .get_java_vm()
            .and_then(|vm| env.new_global_ref(callback).map(|callback| (vm, callback)))
        {
            Ok((vm, callback)) => {
                Some(Arc::new(JniClauneJsCallback { vm, callback }) as Arc<dyn ClauneJsCallback>)
            }
            Err(error) => {
                return ShellOutput {
                    exit_code: 1,
                    stdout: String::new(),
                    stderr: format!("failed to create claune-js callback: {error}\n"),
                    duration_ms: None,
                    error: Some("failed to create claune-js callback".to_string()),
                    stdout_truncated: false,
                    stderr_truncated: false,
                };
            }
        }
    };

    execute_blocking(
        workspace_root,
        &command,
        u64::try_from(timeout_ms).ok().filter(|value| *value > 0),
        callback,
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::sync::Mutex;

    struct RecordingCallback {
        calls: Mutex<Vec<(String, Vec<String>, String)>>,
    }

    impl RecordingCallback {
        fn new() -> Self {
            Self {
                calls: Mutex::new(Vec::new()),
            }
        }
    }

    impl ClauneJsCallback for RecordingCallback {
        fn run(&self, script_path: &str, argv: &[String], stdin: &str) -> ClauneJsOutput {
            self.calls.lock().unwrap().push((
                script_path.to_string(),
                argv.to_vec(),
                stdin.to_string(),
            ));
            ClauneJsOutput {
                exit_code: 0,
                stdout: format!("{script_path}:{}:{stdin}", argv.join(",")),
                stderr: String::new(),
            }
        }
    }

    #[test]
    fn executes_in_real_workspace_with_jq() {
        let temp = tempfile_dir();
        fs::write(temp.join("input.json"), r#"{"name":"claune"}"#).unwrap();

        let output = execute_blocking(
            &temp,
            "jq -r .name input.json > output.txt && cat output.txt",
            Some(5_000),
            None,
        );

        assert_eq!(output.exit_code, 0, "{output:?}");
        assert_eq!(output.stdout, "claune\n");
        assert_eq!(
            fs::read_to_string(temp.join("output.txt")).unwrap(),
            "claune\n"
        );
    }

    #[test]
    fn claune_js_builtin_calls_callback_with_args_and_stdin() {
        let temp = tempfile_dir();
        let callback = Arc::new(RecordingCallback::new());

        let output = execute_blocking(
            &temp,
            "printf payload | claune-js scripts/action.js one two",
            Some(5_000),
            Some(callback),
        );

        assert_eq!(output.exit_code, 0, "{output:?}");
        assert_eq!(output.stdout, "scripts/action.js:one,two:payload");
    }

    #[test]
    fn claune_js_builtin_accepts_inline_script_from_stdin() {
        let temp = tempfile_dir();
        let callback = Arc::new(RecordingCallback::new());

        let output = execute_blocking(
            &temp,
            "claune-js - one two <<'JS'\nconsole.log(argv.join(','));\nJS",
            Some(5_000),
            Some(callback.clone()),
        );

        assert_eq!(output.exit_code, 0, "{output:?}");
        assert_eq!(output.stdout, "-:one,two:console.log(argv.join(','));\n");
        assert_eq!(
            callback.calls.lock().unwrap().as_slice(),
            &[(
                "-".to_string(),
                vec!["one".to_string(), "two".to_string()],
                "console.log(argv.join(','));\n".to_string(),
            )],
        );
    }

    #[test]
    fn claune_js_reports_missing_callback() {
        let temp = tempfile_dir();

        let output = execute_blocking(&temp, "claune-js script.js", Some(5_000), None);

        assert_eq!(output.exit_code, 127);
        assert!(output.stderr.contains("claune-js is not configured"));
    }

    fn tempfile_dir() -> PathBuf {
        let nanos = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let base = std::env::temp_dir().join(format!(
            "bashkit-bridge-test-{}-{}",
            std::process::id(),
            nanos,
        ));
        fs::create_dir_all(&base).unwrap();
        base
    }
}
