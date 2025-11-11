module org.example.demo {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;

    opens org.example.demo to javafx.fxml;
    exports org.example.demo;
}