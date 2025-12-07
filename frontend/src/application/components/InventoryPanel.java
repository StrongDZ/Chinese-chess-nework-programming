package application.components;

import application.state.UIState;
import application.util.AssetHelper;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import javafx.scene.effect.DropShadow;
import javafx.animation.ScaleTransition;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;

/**
 * Inventory panel that appears when clicking the inventory icon.
 */
public class InventoryPanel extends StackPane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;
    private VBox selectedSlot = null;

    public InventoryPanel(UIState state) {
        this.state = state;
        
        setPrefSize(1920, 1080);
        setLayoutX(0);
        setLayoutY(0);
        setStyle("-fx-background-color: transparent;");
        setPickOnBounds(false);
        
        Pane container = new Pane();
        container.setPrefSize(1920, 1080);
        container.setStyle("-fx-background-color: transparent;");
        container.setMouseTransparent(false);  // allow mouse events to reach children (ic_back hover)
        
        // Tạo inventory content
        StackPane inventoryContent = createInventoryContent();
        inventoryContent.setLayoutX(0);
        inventoryContent.setLayoutY(0);
        inventoryContent.setPickOnBounds(true);
        inventoryContent.setMouseTransparent(false);
        
        container.getChildren().add(inventoryContent);
        getChildren().add(container);
        
        // Bind visibility với MAIN_MENU và inventoryVisible
        visibleProperty().bind(
            state.appStateProperty().isEqualTo(UIState.AppState.MAIN_MENU)
                .and(state.inventoryVisibleProperty())
        );
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        
        // Fade animation khi inventoryVisible thay đổi
        state.inventoryVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && state.appStateProperty().get() == UIState.AppState.MAIN_MENU) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
        
        state.appStateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == UIState.AppState.MAIN_MENU && state.isInventoryVisible()) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
    }
    
    private StackPane createInventoryContent() {
        StackPane content = new StackPane();
        content.setPrefSize(1920, 1080);
        content.setMouseTransparent(false);
        content.setPickOnBounds(true);

        // Background image
        ImageView bg = new ImageView(AssetHelper.image("background2.png"));
        bg.setFitWidth(1920);
        bg.setFitHeight(1080);
        bg.setPreserveRatio(false);

        Pane mainPane = new Pane();
        mainPane.setPrefSize(1920, 1080);
        mainPane.setMouseTransparent(false);
        mainPane.setPickOnBounds(true);

        // Top-left: Back arrow và "Inventory" title
        HBox topBar = new HBox(20);
        topBar.setLayoutX(50);
        topBar.setLayoutY(50);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setMouseTransparent(true); // Cho phép mouse đi qua, chỉ backButton nhận

        // Back arrow icon - tách ra khỏi topBar, đặt trực tiếp vào mainPane
        ImageView backIcon = new ImageView(AssetHelper.image("ic_back.png"));
        backIcon.setFitWidth(171);
        backIcon.setFitHeight(171);
        backIcon.setPreserveRatio(true);
        StackPane backButton = new StackPane(backIcon);
        backButton.setLayoutX(50);
        backButton.setLayoutY(10);
        backButton.setPrefSize(171, 171);
        backButton.setMinSize(171, 171);
        backButton.setMaxSize(171, 171);
        backButton.setCursor(javafx.scene.Cursor.HAND);
        backButton.setMouseTransparent(false);
        backButton.setPickOnBounds(true);

        // Hover effect: scale trực tiếp
        backButton.setOnMouseEntered(e -> {
            backButton.setScaleX(1.1);
            backButton.setScaleY(1.1);
            backIcon.setOpacity(0.85);
        });
        backButton.setOnMouseExited(e -> {
            backButton.setScaleX(1.0);
            backButton.setScaleY(1.0);
            backIcon.setOpacity(1.0);
        });

        // Click handler - đóng inventory (quay về frame 5)
        backButton.setOnMouseClicked(e -> state.closeInventory());

        // "Inventory" title - tách ra khỏi topBar, đặt trực tiếp vào mainPane
        Label inventoryTitle = new Label("Inventory");
        inventoryTitle.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 80px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        inventoryTitle.setLayoutX(50 + 171 + 30);  // Sang phải: sau backButton (50 + 171) + khoảng cách 30
        inventoryTitle.setLayoutY(10);  // Lên trên: từ 50 xuống 30

        // Left navigation panel - chiếm khoảng 1/5 màn hình
        VBox leftNav = new VBox(20);
        leftNav.setLayoutX(50);
        leftNav.setLayoutY(150);
        leftNav.setPrefWidth(350);
        
        // "Board" button - mở rộng đến đường kẻ dọc (x=420)
        Label boardButton = new Label("Board");
        boardButton.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 80px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-alignment: center; " +  // Căn giữa nội dung
            "-fx-text-alignment: center;"  // Căn giữa text
        );
        boardButton.setTranslateY(-15);  // Đẩy chữ lên cao 5px
        
        StackPane boardButtonContainer = new StackPane(boardButton);
        boardButtonContainer.setPrefSize(370, 100);
        boardButtonContainer.setMinSize(370, 100);
        boardButtonContainer.setMaxSize(370, 100);
        boardButtonContainer.setAlignment(Pos.CENTER);  // Đảm bảo StackPane căn giữa
        boardButtonContainer.setStyle(
            "-fx-background-color: rgba(255, 248, 220, 0.8); " +
            "-fx-background-radius: 10; " +
            "-fx-border-radius: 10;"
        );
        boardButtonContainer.setCursor(javafx.scene.Cursor.HAND);
        
        leftNav.getChildren().add(boardButtonContainer);
        
        // Load danh sách ảnh từ folder board_final
        List<String> boardImages = loadBoardImages();
        
        // Right content area - slots với scroll
        Pane slotsPane = new Pane();
        slotsPane.setPrefWidth(1400);
        slotsPane.setStyle("-fx-background-color: transparent;");
        
        // Tính toán kích thước và spacing cho slots
        double slotWidth = 280;
        double slotHeight = 400;
        double spacingX = 40;
        double spacingY = 40;
        int colsPerRow = 4;  // Số cột mỗi hàng
        double startX = 0;
        double startY = 0;
        
        // Tạo slots dựa trên số ảnh
        int totalSlots = boardImages.size();
        for (int i = 0; i < totalSlots; i++) {
            int row = i / colsPerRow;
            int col = i % colsPerRow;
            
            double x = startX + col * (slotWidth + spacingX);
            double y = startY + row * (slotHeight + spacingY);
            
            String imagePath = boardImages.get(i);
            VBox slot = createItemSlot(imagePath);
            slot.setLayoutX(x);
            slot.setLayoutY(y);
            slotsPane.getChildren().add(slot);
        }
        
        // Tính chiều cao cần thiết cho slotsPane
        int totalRows = (int) Math.ceil((double) totalSlots / colsPerRow);
        double totalHeight = startY + totalRows * (slotHeight + spacingY);
        slotsPane.setPrefHeight(totalHeight);
        
        // ScrollPane để chứa slotsPane
        ScrollPane scrollPane = new ScrollPane(slotsPane);
        scrollPane.setLayoutX(450);
        scrollPane.setLayoutY(150);
        scrollPane.setPrefWidth(1400);
        scrollPane.setPrefHeight(800);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);  // Ẩn scrollbar
        scrollPane.setStyle(
            "-fx-background: transparent; " +
            "-fx-background-color: transparent; " +
            "-fx-border-color: transparent; " +
            "-fx-border-width: 0;"
        );
        scrollPane.setFitToWidth(true);
        
        // Làm viewport và scrollbar trong suốt sau khi scene được tạo
        scrollPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                javafx.scene.Node viewport = scrollPane.lookup(".viewport");
                if (viewport != null) {
                    viewport.setStyle("-fx-background-color: transparent;");
                }
                javafx.scene.Node vScrollBar = scrollPane.lookup(".scroll-bar:vertical");
                if (vScrollBar != null) {
                    vScrollBar.setStyle("-fx-opacity: 0; -fx-pref-width: 0;");
                }
                javafx.scene.Node hScrollBar = scrollPane.lookup(".scroll-bar:horizontal");
                if (hScrollBar != null) {
                    hScrollBar.setStyle("-fx-opacity: 0; -fx-pref-height: 0;");
                }
            }
        });
        
        // Đường kẻ dọc phân cách giữa leftNav và slotsContainer
        Line verticalDivider = new Line();
        verticalDivider.setStartX(420);  // Vị trí giữa leftNav (50 + 350) và slotsContainer (450)
        verticalDivider.setStartY(150);
        verticalDivider.setEndX(420);
        verticalDivider.setEndY(950);  // Kéo dài xuống gần cuối màn hình
        verticalDivider.setStroke(Color.color(0.5, 0.5, 0.5, 0.6));  // Màu xám nhạt, trong suốt
        verticalDivider.setStrokeWidth(1.5);
        // Thêm shadow cho đường kẻ dọc
        DropShadow verticalShadow = new DropShadow();
        verticalShadow.setColor(Color.color(0, 0, 0, 0.3));
        verticalShadow.setRadius(3);
        verticalShadow.setOffsetX(1);
        verticalShadow.setOffsetY(1);
        verticalDivider.setEffect(verticalShadow);
        
        // Đường kẻ ngang ở phía trên các item slots
        Line horizontalDivider = new Line();
        horizontalDivider.setStartX(450);  // Bắt đầu từ vị trí slotsContainer
        horizontalDivider.setStartY(150);  // Ở phía trên, ngay dưới topBar và leftNav
        horizontalDivider.setEndX(1850);  // Kéo dài đến gần cuối màn hình
        horizontalDivider.setEndY(150);
        horizontalDivider.setStroke(Color.color(0.5, 0.5, 0.5, 0.6));  // Màu xám nhạt, trong suốt
        horizontalDivider.setStrokeWidth(1.5);
        // Thêm shadow cho đường kẻ ngang
        DropShadow horizontalShadow = new DropShadow();
        horizontalShadow.setColor(Color.color(0, 0, 0, 0.3));
        horizontalShadow.setRadius(3);
        horizontalShadow.setOffsetX(1);
        horizontalShadow.setOffsetY(1);
        horizontalDivider.setEffect(horizontalShadow);
        
        // Thứ tự add, đảm bảo backButton và inventoryTitle ở trên cùng
        mainPane.getChildren().addAll(leftNav, verticalDivider, horizontalDivider, topBar, backButton, inventoryTitle, scrollPane);
        content.getChildren().addAll(bg, mainPane);
        return content;
    }
    
    /**
     * Load danh sách các file ảnh từ folder board_final
     */
    private List<String> loadBoardImages() {
        List<String> images = new ArrayList<>();
        try {
            Path assetRoot = AssetHelper.getAssetRoot();
            Path boardFolder = assetRoot.resolve("board_final");
            
            if (boardFolder.toFile().exists() && boardFolder.toFile().isDirectory()) {
                File[] files = boardFolder.toFile().listFiles((dir, name) -> {
                    String lower = name.toLowerCase();
                    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
                });
                
                if (files != null) {
                    for (File file : files) {
                        // Tạo path relative: "board_final/filename"
                        images.add("board_final/" + file.getName());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading board images: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Nếu không có ảnh nào, trả về danh sách rỗng
        return images;
    }
    
    private VBox createItemSlot(String imagePath) {
        VBox slot = new VBox(10);
        slot.setPrefSize(280, 400);
        slot.setAlignment(Pos.CENTER);
        slot.setPadding(new Insets(15));
        slot.setStyle("-fx-background-color: transparent;");
        
        // Slot background
        Rectangle slotBg = new Rectangle(250, 320);
        slotBg.setFill(Color.TRANSPARENT);
        slotBg.setStroke(Color.color(0.6, 0.4, 0.3));
        slotBg.setStrokeWidth(2);
        slotBg.setArcWidth(8);
        slotBg.setArcHeight(8);
        
        // ImageView để hiển thị ảnh board
        ImageView boardImage = null;
        if (imagePath != null && !imagePath.isEmpty()) {
            try {
                Image img = AssetHelper.image(imagePath);
                boardImage = new ImageView(img);
                boardImage.setFitWidth(246);  // Nhỏ hơn slotBg một chút để có border
                boardImage.setFitHeight(316);
                boardImage.setPreserveRatio(true);
                boardImage.setSmooth(true);
            } catch (Exception e) {
                System.err.println("Error loading image: " + imagePath);
            }
        }
        
        StackPane slotContent = new StackPane();
        slotContent.setStyle("-fx-background-color: transparent;");
        slotContent.getChildren().add(slotBg);
        if (boardImage != null) {
            slotContent.getChildren().add(boardImage);
        }
        
        // "Choose" button - ở dưới slot
        Label chooseButton = new Label("Choose");
        chooseButton.setPrefSize(200, 50);
        chooseButton.setAlignment(Pos.CENTER);
        chooseButton.setStyle(normalButtonStyle());
        chooseButton.setCursor(javafx.scene.Cursor.HAND);
        
        // Đặt initial scale
        chooseButton.setScaleX(1.0);
        chooseButton.setScaleY(1.0);
        
        // Tạo scale transition cho hover effect
        ScaleTransition btnScaleIn = new ScaleTransition(Duration.millis(150), chooseButton);
        btnScaleIn.setToX(1.06);
        btnScaleIn.setToY(1.06);
        
        ScaleTransition btnScaleOut = new ScaleTransition(Duration.millis(150), chooseButton);
        btnScaleOut.setToX(1.0);
        btnScaleOut.setToY(1.0);
        
        // Hiệu ứng khi trỏ chuột vào
        chooseButton.setOnMouseEntered(e -> {
            btnScaleOut.stop();
            btnScaleIn.setFromX(chooseButton.getScaleX());
            btnScaleIn.setFromY(chooseButton.getScaleY());
            btnScaleIn.play();
        });
        
        // Hiệu ứng khi trỏ chuột ra ngoài
        chooseButton.setOnMouseExited(e -> {
            btnScaleIn.stop();
            btnScaleOut.setFromX(chooseButton.getScaleX());
            btnScaleOut.setFromY(chooseButton.getScaleY());
            btnScaleOut.play();
        });
        
        // Chọn slot duy nhất
        chooseButton.setOnMouseClicked(e -> selectSlot(slot, chooseButton));
        
        slot.getChildren().addAll(slotContent, chooseButton);
        
        return slot;
    }
    
    private String normalButtonStyle() {
        return "-fx-font-family: 'Lily Script One'; "
             + "-fx-font-size: 22px; "
             + "-fx-text-fill: white; "
             + "-fx-background-color: rgba(80, 80, 80, 0.9); "
             + "-fx-background-radius: 8; "
             + "-fx-border-radius: 8; "
             + "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.3), 5, 0, 0, 2);";
    }
    
    private String selectedButtonStyle() {
        return "-fx-font-family: 'Lily Script One'; "
             + "-fx-font-size: 22px; "
             + "-fx-text-fill: white; "
             + "-fx-background-color: rgba(40, 120, 200, 0.9); "
             + "-fx-background-radius: 8; "
             + "-fx-border-radius: 8; "
             + "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.3), 5, 0, 0, 2);";
    }
    
    private void selectSlot(VBox slot, Label chooseButton) {
        // Bỏ chọn slot cũ nếu có
        if (selectedSlot != null) {
            // Button luôn là phần tử thứ 2 trong VBox (sau slotContent)
            Label oldBtn = (Label) selectedSlot.getChildren().get(1);
            oldBtn.setText("Choose");
            oldBtn.setStyle(normalButtonStyle());
        }
        // Chọn slot mới
        selectedSlot = slot;
        chooseButton.setText("Selected");
        chooseButton.setStyle(selectedButtonStyle());
    }
    
    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
}
