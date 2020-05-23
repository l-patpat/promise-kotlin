package aya.patpat.promise.action;

import aya.patpat.promise.Promise;
import aya.patpat.result.GlobalResult;

public interface AsyncCatchAction {
    void run(GlobalResult result, Promise promise);
}
