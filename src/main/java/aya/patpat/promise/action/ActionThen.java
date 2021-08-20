package aya.patpat.promise.action;

public interface ActionThen {
    void run(Object data, Action<Object> resolve);
}
