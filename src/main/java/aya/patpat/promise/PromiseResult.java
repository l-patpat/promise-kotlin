package aya.patpat.promise;

public class PromiseResult {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILURE = "FAILURE";
    public static final String CANCEL = "CANCEL";
    public static final String ABORT = "ABORT";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String BUSY = "BUSY";
    public static final String NOTHING = "NOTHING";
    public static final String NOT_AUTH = "NOT_AUTO";
    public static final String NONSUPPORT = "NONSUPPORT";
    public static final String ERR_INTERNAL = "ERR_INTERNAL";
    public static final String ERR_SERVER = "ERR_SERVER";
    public static final String ERR_PARAMS = "ERR_PARAMS";
    public static final String ERR_NETWORK = "ERR_NETWORK";

    private String result = "";
    private String msg = "";

    public PromiseResult(String result) {
        this(result, "");
    }
    public PromiseResult(String result, String msg) {
        this.result = result;
        this.msg = msg;
    }

    public String getResult() { return this.result == null ? "" : this.result; }
    public void setResult(String result) { this.result = result; }

    public String getMsg() { return this.msg == null ? "" : this.msg; }
    public void setMsg(String msg) { this.msg = msg; }

    public boolean isSuccess() {
        return SUCCESS.equals(getResult());
    }

    public boolean is(String result) {
        return result != null && result.equals(this.result);
    }

    public String toString() {
        return String.format("{result: \"%s\"; msg: \"%s\"}", result, msg);
    }

    public static class Data<T> extends PromiseResult {
        public T data;
        public Data(T data, String result, String msg) {
            super(result, msg);
            this.data = data;
        }
    }

    public static class Success extends PromiseResult {
        public Success() { super(SUCCESS, "操作成功"); }
        public Success(String msg) { super(SUCCESS, msg); }
    }

    public static class SuccessWith<T> extends Data<T> {
        public SuccessWith(T data) { super(data, SUCCESS, "操作成功"); }
        public SuccessWith(T data, String msg) { super(data, SUCCESS, msg); }
    }

    public static class Failure extends PromiseResult {
        public Failure() { super(FAILURE, "操作失败"); }
        public Failure(String msg) { super(FAILURE, msg); }
    }

    public static class Cancel extends PromiseResult {
        public Cancel() { super(CANCEL, "取消"); }
        public Cancel(String msg) { super(CANCEL, msg); }
    }

    public static class Abort extends PromiseResult {
        public Abort() { super(ABORT, "异常中止"); }
        public Abort(String msg) { super(ABORT, msg); }
    }

    public static class Timeout extends PromiseResult {
        public Timeout() { super(TIMEOUT, "操作超时"); }
        public Timeout(String msg) { super(TIMEOUT, msg); }
    }

    public static class Busy extends PromiseResult {
        public Busy() { super(BUSY, "操作忙"); }
        public Busy(String msg) { super(BUSY, msg); }
    }

    public static class Nothing extends PromiseResult {
        public Nothing() { super(NOTHING, "空操作/空数据"); }
        public Nothing(String msg) { super(NOTHING, msg); }
    }

    public static class NotAuth extends PromiseResult {
        public NotAuth() { super(NOTHING, ""); }
        public NotAuth(String msg) { super(NOTHING, msg); }
    }

    public static class ErrInternal extends PromiseResult {
        public ErrInternal() { super(ERR_INTERNAL, "内部错误"); }
        public ErrInternal(String msg) { super(ERR_INTERNAL, msg); }
    }

    public static class ErrServer extends PromiseResult {
        public ErrServer() { super(ERR_SERVER, "服务器故障"); }
        public ErrServer(String msg) { super(ERR_SERVER, msg); }
    }

    public static class ErrParams extends PromiseResult {
        public ErrParams() { super(ERR_PARAMS, "参数无效"); }
        public ErrParams(String msg) { super(ERR_PARAMS, msg); }
    }

    public static class ErrNetwork extends PromiseResult {
        public ErrNetwork() { super(ERR_NETWORK, "网络错误"); }
        public ErrNetwork(String msg) { super(ERR_NETWORK, msg); }
    }
}
