import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class POSGridView extends JPanel {
    private JPanel productGrid;
    private JPanel orderPanel;
    private JTextArea orderTextArea;
    private JLabel subtotalLabel, taxLabel, totalLabel;
    private List<OrderItem> orderItems = new ArrayList<>();
    private final double TAX_RATE = 0.10;
    private JButton placeOrderButton;
    private Map<Integer, ProductCard> productCards = new HashMap<>();
    private String currentPaymentMethod = "Cash";
    private JTextField searchField;
    private Map<Integer, String> categories = new HashMap<>();
    private JPanel categoryButtonPanel;
    private Map<String, JButton> categoryButtons = new LinkedHashMap<>();

    public POSGridView() {
        initializeUI();
        loadCategoriesFromDatabase();
        loadProductsFromDatabase();
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create control panel at the top
        JPanel controlPanel = new JPanel(new BorderLayout(10, 10));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        
        // Search panel
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(200, 30));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterProducts(); }
            @Override public void removeUpdate(DocumentEvent e) { filterProducts(); }
            @Override public void changedUpdate(DocumentEvent e) { filterProducts(); }
        });
        
        JLabel searchLabel = new JLabel("Search:");
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        
        // Category filter panel - now using buttons
        categoryButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        categoryButtonPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        // Add "All" button by default
        JButton allButton = createCategoryButton("All");
        allButton.setSelected(true);
        categoryButtonPanel.add(allButton);
        categoryButtons.put("All", allButton);
        
        // Add components to control panel
        controlPanel.add(searchPanel, BorderLayout.WEST);
        controlPanel.add(categoryButtonPanel, BorderLayout.CENTER);
        
        // Product grid setup - using BoxLayout for vertical arrangement
        productGrid = new JPanel();
        productGrid.setLayout(new BoxLayout(productGrid, BoxLayout.Y_AXIS));
        productGrid.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        productGrid.setBackground(Color.WHITE);

        JScrollPane productScrollPane = new JScrollPane(productGrid);
        productScrollPane.getVerticalScrollBar().setUnitIncrement(20);
        productScrollPane.setPreferredSize(new Dimension(900, 650));

        // Order summary panel
        orderPanel = createOrderPanel();

        // Add components to main panel
        add(controlPanel, BorderLayout.NORTH);
        add(productScrollPane, BorderLayout.CENTER);
        add(new JScrollPane(orderPanel), BorderLayout.EAST);
    }

    private JButton createCategoryButton(String categoryName) {
        JButton button = new JButton(categoryName);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBackground(new Color(240, 240, 240));
        button.setForeground(new Color(70, 70, 70));
        button.setFont(new Font("Arial", Font.PLAIN, 14));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));
        
        // Add icon if available
        String iconText = getCategoryIcon(categoryName);
        if (!iconText.isEmpty()) {
            button.setText(iconText + " " + categoryName);
        }
        
        button.addActionListener(e -> {
            // Deselect all buttons
            for (JButton btn : categoryButtons.values()) {
                btn.setSelected(false);
                btn.setBackground(new Color(240, 240, 240));
                btn.setForeground(new Color(70, 70, 70));
            }
            
            // Select current button
            button.setSelected(true);
            button.setBackground(new Color(50, 120, 200));
            button.setForeground(Color.WHITE);
            
            filterProducts();
        });
        
        return button;
    }

    private String getCategoryIcon(String categoryName) {
        // Map categories to appropriate icons (using emojis as placeholders)
        switch(categoryName.toLowerCase()) {
            case "beverages": return "🥤";
            case "food": return "🍔";
            case "snacks": return "🍪";
            case "all": return "🛒";
            default: return "";
        }
    }

    private void filterProducts() {
        String searchText = searchField.getText().toLowerCase();
        String selectedCategory = getSelectedCategory();
        
        productGrid.removeAll();
        
        // Group products by category
        Map<String, List<ProductCard>> categorizedProducts = new LinkedHashMap<>();
        
        for (ProductCard card : productCards.values()) {
            boolean matchesSearch = card.getName().toLowerCase().contains(searchText);
            boolean matchesCategory = selectedCategory.equals("All") || 
                                   card.getCategoryName().equals(selectedCategory);
            
            if (matchesSearch && matchesCategory) {
                categorizedProducts
                    .computeIfAbsent(card.getCategoryName(), k -> new ArrayList<>())
                    .add(card);
            }
        }
        
        // Add category sections with headers similar to the photo
        for (Map.Entry<String, List<ProductCard>> entry : categorizedProducts.entrySet()) {
            // Add category header with style similar to the photo
            productGrid.add(createCategoryHeader(entry.getKey()));
            
            // Create panel for this category's products
            JPanel categoryPanel = new JPanel(new GridLayout(0, 4, 15, 15));
            categoryPanel.setBackground(Color.WHITE);
            
            for (ProductCard card : entry.getValue()) {
                categoryPanel.add(card);
            }
            
            productGrid.add(categoryPanel);
            productGrid.add(Box.createVerticalStrut(20));
        }
        
        productGrid.revalidate();
        productGrid.repaint();
    }

    private String getSelectedCategory() {
        for (Map.Entry<String, JButton> entry : categoryButtons.entrySet()) {
            if (entry.getValue().isSelected()) {
                return entry.getKey();
            }
        }
        return "All";
    }

    private JPanel createCategoryHeader(String categoryName) {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(230, 230, 250));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 220)),
            BorderFactory.createEmptyBorder(8, 15, 8, 15)
        ));
        
        JLabel nameLabel = new JLabel(categoryName);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 16));
        nameLabel.setForeground(new Color(70, 70, 70));
        
        // Icon - using emoji as placeholder
        JLabel iconLabel = new JLabel(getCategoryIcon(categoryName));
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        
        headerPanel.add(iconLabel, BorderLayout.WEST);
        headerPanel.add(nameLabel, BorderLayout.CENTER);
        
        return headerPanel;
    }

    private void loadCategoriesFromDatabase() {
        String query = "SELECT id, name FROM category";
        
        try (Connection con = DBConnection.getConnection();
             PreparedStatement stmt = con.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                categories.put(id, name);
                
                // Add category button
                JButton button = createCategoryButton(name);
                categoryButtonPanel.add(button);
                categoryButtons.put(name, button);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error loading categories: " + e.getMessage());
            // Load sample categories if database fails
            loadSampleCategories();
        }
        
        categoryButtonPanel.revalidate();
        categoryButtonPanel.repaint();
    }

    private void loadSampleCategories() {
        categories.put(1, "Beverages");
        categories.put(2, "Food");
        categories.put(3, "Snacks");
        
        // Add buttons for sample categories
        for (String category : new String[]{"Beverages", "Food", "Snacks"}) {
            JButton button = createCategoryButton(category);
            categoryButtonPanel.add(button);
            categoryButtons.put(category, button);
        }
    }

    private void loadProductsFromDatabase() {
        String query = "SELECT p.id, p.name, p.price, p.image, p.stock_qty, c.name AS category_name " +
                      "FROM product p LEFT JOIN category c ON p.CatID = c.id";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement stmt = con.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                double price = rs.getDouble("price");
                String imageName = rs.getString("image");
                int stockQty = rs.getInt("stock_qty");
                String categoryName = rs.getString("category_name");

                String imagePath = "D:/Y3S2/javaII/Testing_Java/src/Products/" + imageName;
                addProduct(id, name, price, imagePath, stockQty, categoryName);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error loading products: " + e.getMessage());
            loadSampleProducts();
        }
    }

    private void loadSampleProducts() {
        addProduct(1, "Original Count Next Buyer With One New Veg", 23.99, "no_image.jpg", 5, "Food");
        addProduct(2, "Fresh Orange Juice With Real Food", 23.99, "no_image.jpg", 10, "Beverages");
        addProduct(3, "Hard Sun/Head With Truck Shop", 0.00, "no_image.jpg", 8, "Snacks");
        addProduct(4, "Focus Sales With Chicken", 16.00, "no_image.jpg", 7, "Food");
        addProduct(5, "Trading Vegetable Sales - Happy Fruit", 1.00, "no_image.jpg", 15, "Food");
        addProduct(6, "Orange Juice With Real Food on Sugar", 5.99, "no_image.jpg", 20, "Beverages");
        addProduct(7, "Orange Cream Buyer With Fresh Green", 0.00, "no_image.jpg", 5, "Snacks");
        addProduct(8, "Apple &Farm", 2.99, "no_image.jpg", 50, "Food");
    }

    private void addProduct(int productId, String name, double price, String imgPath, int stockQty, String categoryName) {
        ProductCard card = new ProductCard(productId, name, price, imgPath, stockQty, categoryName);
        productCards.put(productId, card);
        filterProducts(); // This will add the product to the grid if it matches current filters
    }

    private JPanel createOrderPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Order Summary"));
        panel.setPreferredSize(new Dimension(350, getHeight()));

        // Order items display
        orderTextArea = new JTextArea(15, 20);
        orderTextArea.setEditable(false);
        orderTextArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        panel.add(new JScrollPane(orderTextArea));

        // Totals panel
        panel.add(createTotalsPanel());

        // Payment methods
        panel.add(createPaymentPanel());

        // Action buttons
        panel.add(createActionPanel());

        return panel;
    }

    private JPanel createTotalsPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

        subtotalLabel = new JLabel("$0.00", JLabel.RIGHT);
        taxLabel = new JLabel("$0.00", JLabel.RIGHT);
        totalLabel = new JLabel("$0.00", JLabel.RIGHT);

        subtotalLabel.setFont(new Font("Arial", Font.BOLD, 14));
        taxLabel.setFont(new Font("Arial", Font.BOLD, 14));
        totalLabel.setFont(new Font("Arial", Font.BOLD, 16));

        panel.add(new JLabel("Subtotal:"));
        panel.add(subtotalLabel);
        panel.add(new JLabel("Tax (10%):"));
        panel.add(taxLabel);
        panel.add(new JLabel("Total:"));
        panel.add(totalLabel);

        return panel;
    }
    
    private JPanel createPaymentPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Payment Method"));

        JButton cashButton = new JButton("Cash");
        JButton cardButton = new JButton("Card");
        JButton qrButton = new JButton("QR Code");

        cashButton.setBackground(new Color(220, 220, 255));
        cardButton.setBackground(new Color(220, 220, 255));
        qrButton.setBackground(new Color(220, 220, 255));

        cashButton.addActionListener(e -> setPaymentMethod("Cash"));
        cardButton.addActionListener(e -> setPaymentMethod("Card"));
        qrButton.addActionListener(e -> {
            setPaymentMethod("QRCode");
            generateAndShowQRCode(calculateTotal());
        });

        panel.add(cashButton);
        panel.add(cardButton);
        panel.add(qrButton);

        return panel;
    }

    private JPanel createActionPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 5, 5));

        placeOrderButton = new JButton("Place Order");
        JButton clearButton = new JButton("Clear Order");

        placeOrderButton.setBackground(new Color(9, 214, 39));
        clearButton.setBackground(new Color(186, 4, 65));

        placeOrderButton.addActionListener(e -> placeOrder());
        clearButton.addActionListener(e -> clearOrder());

        panel.add(placeOrderButton);
        panel.add(clearButton);

        return panel;
    }

    private void addToOrder(int productId, String name, double price) {
        ProductCard card = productCards.get(productId);

        if (card == null) {
            JOptionPane.showMessageDialog(this, "Product not found!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (card.getStockQty() <= 0) {
            JOptionPane.showMessageDialog(this, "Product out of stock!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        card.decreaseStock();

        for (OrderItem item : orderItems) {
            if (item.getProductId() == productId) {
                item.incrementQuantity();
                updateOrderDisplay();
                return;
            }
        }

        orderItems.add(new OrderItem(productId, name, price));
        updateOrderDisplay();
    }

    private void updateOrderDisplay() {
        StringBuilder sb = new StringBuilder();
        double subtotal = 0.0;

        for (OrderItem item : orderItems) {
            sb.append(String.format("%-30s %2d x $%6.2f\n",
                    truncateName(item.getName()), item.getQuantity(), item.getPrice()));
            subtotal += item.getPrice() * item.getQuantity();
        }

        double tax = subtotal * TAX_RATE;
        double total = subtotal + tax;

        orderTextArea.setText(sb.toString());
        subtotalLabel.setText(String.format("$%.2f", subtotal));
        taxLabel.setText(String.format("$%.2f", tax));
        totalLabel.setText(String.format("$%.2f", total));
        placeOrderButton.setEnabled(!orderItems.isEmpty());
    }

    private void placeOrder() {
        if (orderItems.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No items in order!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);

            int orderId = createOrderRecord(con);
            addOrderItems(con, orderId);
            updateProductStocksInDB(con);

            con.commit();
            showReceipt(orderId);
            clearOrder();

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error processing order: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private int createOrderRecord(Connection con) throws SQLException {
        int staffId = getLoggedInStaffId();
        double total = calculateTotal();

        String paymentMethod;
        switch(currentPaymentMethod.toUpperCase()) {
            case "CASH": paymentMethod = "CASH"; break;
            case "CARD":
            case "CREDIT CARD":
            case "DEBIT CARD": paymentMethod = "CARD"; break;
            case "QR":
            case "QR CODE":
            case "QRCODE": paymentMethod = "QRCODE"; break;
            default: throw new SQLException("Invalid payment method: " + currentPaymentMethod);
        }

        String sql = "INSERT INTO sale (sale_date, payment_method, total_amount, staff_id) " +
                     "VALUES (CURRENT_TIMESTAMP, ?, ?, ?)";

        try (PreparedStatement stmt = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, paymentMethod);
            stmt.setDouble(2, total);
            stmt.setInt(3, staffId);

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create order record");
    }

    private void generateAndShowQRCode(double amount) {
        try {
            String qrContent = "POS Payment\nAmount: $" + String.format("%.2f", amount) + 
                             "\nDate: " + new java.util.Date();

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(
                qrContent, 
                BarcodeFormat.QR_CODE, 
                300, 300
            );

            BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
            JLabel qrLabel = new JLabel(new ImageIcon(qrImage));
            JOptionPane.showMessageDialog(
                this, 
                qrLabel, 
                "Scan QR Code to Pay $" + String.format("%.2f", amount), 
                JOptionPane.PLAIN_MESSAGE
            );

        } catch (WriterException e) {
            JOptionPane.showMessageDialog(this, 
                "Failed to generate QR code: " + e.getMessage(),
                "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setPaymentMethod(String method) {
        currentPaymentMethod = method;
        JOptionPane.showMessageDialog(this, "Payment method set to: " + method, 
            "Payment Method", JOptionPane.INFORMATION_MESSAGE);
    }

    private void addOrderItems(Connection con, int saleId) throws SQLException {
        String sql = "INSERT INTO sale_details (qty, unit_price, pid, sale_id) VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = con.prepareStatement(sql)) {
            for (OrderItem item : orderItems) {
                stmt.setInt(1, item.getQuantity()); 
                stmt.setDouble(2, item.getPrice()); 
                stmt.setInt(3, item.getProductId()); 
                stmt.setInt(4, saleId);          
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private int getLoggedInStaffId() {
        if (System.getProperty("staff.id") != null) {
            return Integer.parseInt(System.getProperty("staff.id"));
        }
        return 1; // default staff ID if not set
    }

    private void updateProductStocksInDB(Connection con) throws SQLException {
        String sql = "UPDATE product SET stock_qty = stock_qty - ? WHERE id = ?";

        try (PreparedStatement stmt = con.prepareStatement(sql)) {
            for (OrderItem item : orderItems) {
                stmt.setInt(1, item.getQuantity());
                stmt.setInt(2, item.getProductId());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private double calculateTotal() {
        double subtotal = orderItems.stream()
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum();
        return subtotal + (subtotal * TAX_RATE);
    }

    private void showReceipt(int orderId) {
        StringBuilder receipt = new StringBuilder();
        receipt.append("=== ORDER RECEIPT ===\n");
        receipt.append("Order #").append(orderId).append("\n\n");
        receipt.append("Items:\n");

        for (OrderItem item : orderItems) {
            receipt.append(String.format("%-25s %2d x $%6.2f\n",
                    truncateName(item.getName()), item.getQuantity(), item.getPrice()));
        }

        double subtotal = orderItems.stream()
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum();
        double tax = subtotal * TAX_RATE;
        double total = subtotal + tax;

        receipt.append("\nSubtotal: $").append(String.format("%.2f", subtotal)).append("\n");
        receipt.append("Tax (10%): $").append(String.format("%.2f", tax)).append("\n");
        receipt.append("Total: $").append(String.format("%.2f", total)).append("\n\n");
        receipt.append("Payment Method: ").append(currentPaymentMethod).append("\n");
        receipt.append("Thank you for your order!");

        JOptionPane.showMessageDialog(this, receipt.toString(), "Order Placed", JOptionPane.INFORMATION_MESSAGE);
    }

    private void clearOrder() {
        orderItems.clear();
        updateOrderDisplay();
    }

    private String truncateName(String name) {
        return name.length() > 30 ? name.substring(0, 27) + "..." : name;
    }

    private class ProductCard extends JPanel {
        private int productId;
        private String name;
        private double price;
        private int stockQty;
        private String categoryName;
        private JLabel stockLabel;
        private JButton addButton;

        public ProductCard(int productId, String name, double price, String imgPath, int stockQty, String categoryName) {
            this.productId = productId;
            this.name = name;
            this.price = price;
            this.stockQty = stockQty;
            this.categoryName = categoryName != null ? categoryName : "Uncategorized";
            initializeUI(imgPath);
        }

        private void initializeUI(String imgPath) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(200, 240));

            // Product image
            JLabel imgLabel = new JLabel();
            imgLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            imgLabel.setIcon(createImageIcon(imgPath));
            add(Box.createVerticalStrut(5));
            add(imgLabel);

            // Product info
            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            infoPanel.setBackground(Color.WHITE);
            infoPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel nameLabel = new JLabel("<html><center>" + name + "</center></html>", JLabel.CENTER);
            JLabel priceLabel = new JLabel(String.format("$%.2f", price), JLabel.CENTER);
            stockLabel = new JLabel("Stock: " + stockQty, JLabel.CENTER);
            JLabel categoryLabel = new JLabel(categoryName, JLabel.CENTER);

            nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            priceLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            stockLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            categoryLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            priceLabel.setFont(new Font("Arial", Font.BOLD, 14));
            stockLabel.setFont(new Font("Arial", Font.PLAIN, 12));
            categoryLabel.setFont(new Font("Arial", Font.ITALIC, 11));
            categoryLabel.setForeground(Color.GRAY);

            infoPanel.add(Box.createVerticalStrut(5));
            infoPanel.add(nameLabel);
            infoPanel.add(priceLabel);
            infoPanel.add(stockLabel);
            infoPanel.add(categoryLabel);
            infoPanel.add(Box.createVerticalStrut(5));

            // Add button
            addButton = new JButton("Add to Order");
            addButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            addButton.setBackground(new Color(2, 240, 90));
            addButton.setForeground(Color.WHITE);
            addButton.setPreferredSize(new Dimension(150, 80));
            
            addButton.addActionListener(e -> {
                POSGridView.this.addToOrder(productId, name, price);
            });

            add(infoPanel);
            add(Box.createVerticalStrut(5));
            add(addButton);
            add(Box.createVerticalStrut(5));

            if (stockQty <= 0) {
                setEnabled(false);
                addButton.setEnabled(false);
            }
        }

        private ImageIcon createImageIcon(String imgPath) {
            File imgFile = new File(imgPath);
            if (imgFile.exists()) {
                return new ImageIcon(new ImageIcon(imgPath).getImage().getScaledInstance(150, 120, Image.SCALE_SMOOTH));
            } else {
                BufferedImage placeholder = new BufferedImage(150, 120, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = placeholder.createGraphics();
                g2.setPaint(Color.LIGHT_GRAY);
                g2.fillRect(0, 0, 150, 120);
                g2.setPaint(Color.BLACK);
                g2.drawString("No Image", 50, 60);
                g2.dispose();
                return new ImageIcon(placeholder);
            }
        }

        public void decreaseStock() {
            if (stockQty > 0) {
                stockQty--;
                stockLabel.setText("Stock: " + stockQty);

                if (stockQty == 0) {
                    setEnabled(false);
                    addButton.setEnabled(false);
                    JOptionPane.showMessageDialog(this, "Product out of stock!", "Info", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }

        public int getStockQty() { return stockQty; }
        public String getName() { return name; }
        public String getCategoryName() { return categoryName; }
        public JButton getAddButton() { return addButton; }
    }

    private static class OrderItem {
        private int productId;
        private String name;
        private double price;
        private int quantity;

        public OrderItem(int productId, String name, double price) {
            this.productId = productId;
            this.name = name;
            this.price = price;
            this.quantity = 1;
        }

        public int getProductId() { return productId; }
        public String getName() { return name; }
        public double getPrice() { return price; }
        public int getQuantity() { return quantity; }

        public void incrementQuantity() { quantity++; }
    }
}


