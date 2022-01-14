package com.idormy.sms.forwarder.utils;

import java.util.Objects;

public class Lamda {
    public interface Consumer<T> extends Func<T, T> {
        void accept(T t) throws Exception;

        default T execute(T t) throws Exception {
            accept(t);
            return t;
        }
    }

    public interface Func<T, R> {
        R execute(T t) throws Exception;

        default R executeThrowRunTimeExcp(T t) {
            try {
                return execute(t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        default R executeIgnoreExcp(T t) {
            try {
                return execute(t);
            } catch (Exception ignored) {
            }
            return null;
        }

        default <E> Func<T, E> andThen(Func<? super R, ? extends E> after) {
            Objects.requireNonNull(after);
            return (T t) -> after.execute(execute(t));
        }
    }
}