package pts;

public class DebugLogger {
    boolean debug_all;
    boolean intraProc;
    boolean interProc;
    boolean disasm;
    boolean typePrint;
    boolean constraintPrint;
    boolean fieldSensitive;
    public boolean param;
    public boolean env;

    DebugLogger() {
        debug_all = true;
        interProc = true;
        intraProc = true;
        disasm = false;
        typePrint = false;
        constraintPrint = false;
        fieldSensitive = true;
        param = true;
        env = true;
    }

    void log(boolean flag, String format, Object... args) {
        if (debug_all && flag) {
            System.out.println(String.format(format, (Object[]) args));
        }
    }

    void loge(boolean flag, String format, Object... args) {
        if (debug_all && flag) {
            System.out.println(String.format(format, (Object[]) args));
        }
    }

    void info(String format, Object... args) {
        System.out.println(String.format(format, args));
    }
}
