module ChineseChessJavaFX {
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires com.google.gson;

    exports application;
    exports application.components;
    exports application.state;
    exports application.network;
    exports application.network.handlers;
    exports application.network.senders;
}

