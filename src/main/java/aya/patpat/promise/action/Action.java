package aya.patpat.promise.action;

public interface Action<T> {
    void run(T value);
}
