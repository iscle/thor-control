# app_process starts the root daemon and the debug CLI by class name: keep their entry points.
-keep class com.thorctl.daemon.Main {
    public static void main(java.lang.String[]);
}
-keep class com.thorctl.daemon.Cli {
    public static void main(java.lang.String[]);
}

# The daemon calls hidden framework classes, compiled against the stubs in :hidden-api, which are
# not packaged: the framework provides them at runtime, so R8 must not warn about them missing.
-dontwarn android.**
-dontwarn com.android.**
