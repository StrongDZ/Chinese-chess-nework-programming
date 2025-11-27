module ChinessChessProject {
	requires javafx.controls;
	requires javafx.graphics;
	
	opens application to javafx.graphics, javafx.fxml;
	opens application.components to javafx.graphics;
}