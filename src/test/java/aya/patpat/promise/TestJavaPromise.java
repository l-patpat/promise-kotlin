package aya.patpat.promise;

import aya.patpat.promise.action.ActionCatch;
import aya.patpat.promise.action.ActionPromise;
import aya.patpat.promise.action.ActionThen;
import aya.patpat.promise.action.NextActionThen;
import org.junit.Test;

public class TestJavaPromise {

    @Test
    void test() throws InterruptedException {
        new Promise(promise -> {
            promise.resolve(1);
        }).onThen(data -> {

        }).onThen((data, next) -> {
            next.run(2);
        }).onThen(data -> {
            System.console().printf("%d", data);
        }).onCatch(result -> {

        }).launch();

        Thread.sleep(1000);
    }
}
