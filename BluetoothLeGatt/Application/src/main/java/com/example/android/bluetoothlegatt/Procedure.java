package com.example.android.bluetoothlegatt;

public interface Procedure {
        void perform();

        static Procedure empty = () -> {};
}