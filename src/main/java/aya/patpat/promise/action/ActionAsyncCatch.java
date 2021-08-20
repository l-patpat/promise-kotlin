package aya.patpat.promise.action;

import aya.patpat.promise.Promise;
import aya.patpat.promise.PromiseResult;

public interface ActionAsyncCatch {
    void run(PromiseResult result, Promise promise);
}
