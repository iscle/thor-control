# app_process starts the root daemon and the debug CLI by class name: keep their entry points.
-keep class com.thorctl.daemon.Main {
    public static void main(java.lang.String[]);
}
-keep class com.thorctl.daemon.Cli {
    public static void main(java.lang.String[]);
}

# Hidden framework classes are reached by reflection (SystemServices); silence warnings about
# platform internals.
-dontwarn android.**
-dontwarn com.android.**
