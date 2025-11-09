import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TimetableGUI.java
 * Provides a graphical user interface for inputting courses, defining schedule constraints,
 * and displaying the generated timetable using the TimetableGenerator logic (from TimetableModel.java).
 */
public class TimetableGUI extends JFrame {

    // --- GUI Components ---
    private final JTextArea courseInputArea;
    private final JTextField daysInput;
    private final JTextField timesInput;
    private final JTextField maxClassesInput;
    private final JTextField minClassesInput;
    private final JButton generateButton;
    private final JTable scheduleTable;
    private final DefaultTableModel tableModel;
    
    // NEW COMPONENTS for schedule management
    private final JButton deleteButton; // Button to delete a selected slot
    private final JButton updateButton; // Button to trigger a re-schedule for a slot
    private TimetableGenerator currentGenerator; // Store the last used generator instance

    // The classes below (Course, ScheduleSlot, TimetableGenerator) are automatically loaded 
    // from the accompanying TimetableModel.java file (the Canvas).

    public TimetableGUI() {
        super("Constraint-Based Timetable Generator");
        
        // Use a clean, modern look-and-feel if available
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Could not set system look and feel.");
        }

        // --- Setup Main Frame ---
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10)); // Outer layout
        
        // --- Input Panel (North) ---
        JPanel inputPanel = new JPanel(new GridLayout(1, 2, 10, 10));

        // LEFT SUB-PANEL: Course Input
        JPanel coursePanel = new JPanel(new BorderLayout(5, 5));
        coursePanel.setBorder(BorderFactory.createTitledBorder("Courses (Name, Professor, Slots)"));
        
        // Default example courses
        courseInputArea = new JTextArea(
            "DSA, Dr. Smith, 3\n" +
            "Algo, Prof. Johnson, 2\n" +
            "AI, Dr. Lee, 3\n" +
            "Ethics, Prof. Johnson, 1", 8, 30
        );
        coursePanel.add(new JScrollPane(courseInputArea), BorderLayout.CENTER);
        inputPanel.add(coursePanel);

        // RIGHT SUB-PANEL: Constraint Input
        JPanel constraintPanel = new JPanel(new GridLayout(5, 2, 5, 5)); 
        constraintPanel.setBorder(BorderFactory.createTitledBorder("Schedule Constraints"));
        
        // Days Input
        constraintPanel.add(new JLabel("Days (e.g., Mon,Tue,Wed):"));
        daysInput = new JTextField("Mon,Tue,Wed,Thu,Fri");
        constraintPanel.add(daysInput);

        // Times Input
        constraintPanel.add(new JLabel("Times (e.g., 9:00,10:00):"));
        timesInput = new JTextField("9:00,10:00,11:00,1:00,2:00,3:00");
        constraintPanel.add(timesInput);

        // Max Classes per Day Input
        constraintPanel.add(new JLabel("Max Classes per Day:"));
        maxClassesInput = new JTextField("3");
        constraintPanel.add(maxClassesInput);
        
        // Min Classes per Day Input
        constraintPanel.add(new JLabel("Min Classes per Day:"));
        minClassesInput = new JTextField("3");
        constraintPanel.add(minClassesInput);

        // Generate Button
        generateButton = new JButton("Generate Timetable");
        generateButton.setBackground(new Color(60, 179, 113)); // Medium Sea Green
        generateButton.setForeground(Color.BLACK);
        generateButton.setFocusPainted(false);
        constraintPanel.add(generateButton);
        
        // Empty placeholder for alignment
        constraintPanel.add(new JLabel()); 

        inputPanel.add(constraintPanel);
        add(inputPanel, BorderLayout.NORTH);

        // --- Output Panel (Center) ---
        tableModel = new DefaultTableModel(new Object[]{"Day", "Time", "Course", "Professor"}, 0);
        scheduleTable = new JTable(tableModel) {
            // Override isCellEditable to make the table read-only
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        scheduleTable.setFillsViewportHeight(true);
        scheduleTable.setRowHeight(40); // Increased row height for better readability
        scheduleTable.getTableHeader().setReorderingAllowed(false);
        
        JPanel outputPanel = new JPanel(new BorderLayout());
        outputPanel.setBorder(BorderFactory.createTitledBorder("Generated Timetable"));
        outputPanel.add(new JScrollPane(scheduleTable), BorderLayout.CENTER);
        
        // --- Schedule Management Panel (South of Output) ---
        JPanel managePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        
        // Delete Button Setup
        deleteButton = new JButton("Delete Selected Slot");
        deleteButton.setBackground(new Color(220, 20, 60)); // Crimson
        deleteButton.setForeground(Color.BLACK);
        deleteButton.setEnabled(false); // Initially disabled
        managePanel.add(deleteButton);
        
        // Update Button Setup
        updateButton = new JButton("Reschedule Selected Slot");
        updateButton.setBackground(new Color(30, 144, 255)); // Dodger Blue
        updateButton.setForeground(Color.BLACK);
        updateButton.setEnabled(false); // Initially disabled
        managePanel.add(updateButton);

        outputPanel.add(managePanel, BorderLayout.SOUTH);
        
        add(outputPanel, BorderLayout.CENTER);

        // --- Event Listeners ---
        generateButton.addActionListener(e -> generateTimetable());
        deleteButton.addActionListener(e -> deleteSelectedSlot()); // NEW Listener
        updateButton.addActionListener(e -> updateSelectedSlot()); // NEW Listener
        
        // Enable buttons only when a row is selected
        scheduleTable.getSelectionModel().addListSelectionListener(e -> {
            boolean isRowSelected = scheduleTable.getSelectedRow() != -1;
            deleteButton.setEnabled(isRowSelected);
            updateButton.setEnabled(isRowSelected);
        });

        // --- Finalize Frame ---
        pack();
        setMinimumSize(new Dimension(800, 600)); // Ensure minimum size for visibility
        setLocationRelativeTo(null); // Center the window
        setVisible(true);
    }

    /**
     * Parses user input and triggers the timetable generation logic.
     */
    private void generateTimetable() {
        try {
            // 1. Parse Courses
            List<Course> courses = parseCourses(courseInputArea.getText());
            if (courses.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter at least one course.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 2. Parse Time Slots
            Map<String, List<String>> timeSlotsMap = parseTimeSlots(daysInput.getText(), timesInput.getText());
            if (timeSlotsMap.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please define at least one day and time slot.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 3. Parse Max and Min Classes per Day
            int maxClassesPerDay = Integer.parseInt(maxClassesInput.getText().trim());
            int minClassesPerDay = Integer.parseInt(minClassesInput.getText().trim());
            
            if (maxClassesPerDay <= 0 || minClassesPerDay <= 0) {
                 JOptionPane.showMessageDialog(this, "Max and Min Classes per Day must be positive integers.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (minClassesPerDay > maxClassesPerDay) {
                JOptionPane.showMessageDialog(this, "Min Classes per Day cannot be greater than Max Classes per Day.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // Check if there are enough slots available
            int totalRequiredSlots = courses.stream().mapToInt(c -> c.requiredSlots).sum();
            int totalPossibleSlots = timeSlotsMap.values().stream().mapToInt(List::size).sum();
            
            if (totalRequiredSlots > totalPossibleSlots) {
                JOptionPane.showMessageDialog(this, 
                    String.format("Not enough total slots. Required: %d, Available: %d.", totalRequiredSlots, totalPossibleSlots), 
                    "Constraint Conflict", JOptionPane.WARNING_MESSAGE);
            }


            // 4. Run the Generator (Passing minClassesPerDay to the generator)
            // NEW: Store the generator instance
            currentGenerator = new TimetableGenerator(courses, timeSlotsMap, maxClassesPerDay, minClassesPerDay);
            List<ScheduleSlot> schedule = currentGenerator.generate();

            // 5. Display the Result
            displaySchedule(schedule, timeSlotsMap);
            
            // Give feedback if generation was not complete
            if (schedule.size() < totalRequiredSlots) {
                int unscheduled = totalRequiredSlots - schedule.size();
                JOptionPane.showMessageDialog(this, 
                    String.format("Warning: Could not schedule %d required slot(s) due to constraints (Max/Min Classes, Contiguity, or professor conflicts).", unscheduled), 
                    "Incomplete Schedule", JOptionPane.WARNING_MESSAGE);
            } else {
                 // Check Min Classes per Day Constraint after generation
                Map<String, Long> classesPerDay = schedule.stream()
                    .collect(Collectors.groupingBy(s -> s.day, Collectors.counting()));
                
                List<String> daysBelowMin = new ArrayList<>();
                for (String day : timeSlotsMap.keySet()) {
                    // Only check days that were intended to be used (have at least one class)
                    if (classesPerDay.getOrDefault(day, 0L) > 0 && classesPerDay.getOrDefault(day, 0L) < minClassesPerDay) {
                        daysBelowMin.add(day);
                    }
                }
                
                if (!daysBelowMin.isEmpty()) {
                    JOptionPane.showMessageDialog(this, 
                        String.format("Warning: The schedule is complete but the Min Classes constraint (%d) was not met on day(s): %s. Try increasing Max Classes or reducing required slots.", 
                            minClassesPerDay, String.join(", ", daysBelowMin)), 
                        "Constraint Violation", JOptionPane.WARNING_MESSAGE);
                }
            }

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid number format for slots or max/min classes per day.", "Input Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "An unexpected error occurred during generation: " + ex.getMessage(), "Generation Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * NEW: Deletes the selected scheduled slot from the timetable.
     */
    private void deleteSelectedSlot() {
        int selectedRow = scheduleTable.getSelectedRow();
        int selectedCol = scheduleTable.getSelectedColumn();

        if (selectedRow == -1 || selectedCol <= 0) { // Column 0 is "Time/Day", skip it
            JOptionPane.showMessageDialog(this, "Please select a scheduled class (a non-empty cell in a Day column) to delete.", "Delete Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Get the day and time from the selected cell's row and column
        String time = (String) tableModel.getValueAt(selectedRow, 0); // Time is always in column 0
        String day = (String) tableModel.getColumnName(selectedCol); // Day is the column header

        // Get the current content (CourseName and ProfessorName via HTML)
        String cellContentHtml = (String) tableModel.getValueAt(selectedRow, selectedCol);
        if (cellContentHtml == null || cellContentHtml.isEmpty() || !cellContentHtml.contains("<html>")) {
             JOptionPane.showMessageDialog(this, "The selected slot is already empty.", "Delete Error", JOptionPane.ERROR_MESSAGE);
             return;
        }

        // Extract Course Name from HTML content (simple parsing for the first bold tag content)
        String courseName = cellContentHtml.substring(cellContentHtml.indexOf("<b>") + 3, cellContentHtml.indexOf("</b>"));

        int confirm = JOptionPane.showConfirmDialog(this, 
            String.format("Are you sure you want to delete the scheduled class '%s' on %s at %s? The required slot count will be re-added.", 
                courseName, day, time), 
            "Confirm Deletion", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            // Remove the slot from the generator's internal schedule
            if (currentGenerator != null) {
                boolean success = currentGenerator.deleteSlot(day, time, courseName);
                
                if (success) {
                    // Update the GUI (redraw the entire schedule from the generator's updated state)
                    displaySchedule(currentGenerator.getCurrentSchedule(), currentGenerator.getTimeSlotsMap());
                    
                    // Update the course input area (to show the slot count has increased)
                    updateCourseInputArea(currentGenerator.getRemainingCourses());

                    JOptionPane.showMessageDialog(this, 
                        String.format("The scheduled slot for '%s' on %s at %s has been deleted and one required slot has been added back to the course. Regenerate to re-schedule it.", 
                            courseName, day, time), 
                        "Deletion Successful", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "Error deleting slot. The slot was not found in the current generator's schedule.", "Deletion Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please generate a timetable first.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * NEW: Re-schedules the selected slot by deleting it and re-running the generation logic 
     * for only that one slot.
     */
    private void updateSelectedSlot() {
         int selectedRow = scheduleTable.getSelectedRow();
        int selectedCol = scheduleTable.getSelectedColumn();

        if (selectedRow == -1 || selectedCol <= 0) {
            JOptionPane.showMessageDialog(this, "Please select a scheduled class to reschedule.", "Update Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Get the day and time from the selected cell's row and column
        String time = (String) tableModel.getValueAt(selectedRow, 0);
        String day = (String) tableModel.getColumnName(selectedCol);
        String cellContentHtml = (String) tableModel.getValueAt(selectedRow, selectedCol);
        
        if (cellContentHtml == null || cellContentHtml.isEmpty() || !cellContentHtml.contains("<html>")) {
             JOptionPane.showMessageDialog(this, "The selected slot is already empty.", "Update Error", JOptionPane.ERROR_MESSAGE);
             return;
        }
        String courseName = cellContentHtml.substring(cellContentHtml.indexOf("<b>") + 3, cellContentHtml.indexOf("</b>"));
        
         int confirm = JOptionPane.showConfirmDialog(this, 
            String.format("Are you sure you want to reschedule the class '%s' on %s at %s? It will be deleted and another random, valid slot will be found.", 
                courseName, day, time), 
            "Confirm Reschedule", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            if (currentGenerator != null) {
                boolean deleteSuccess = currentGenerator.deleteSlot(day, time, courseName);
                
                if (deleteSuccess) {
                    // Re-run generation logic for just the course that had its slot deleted
                    // The generator's internal state now reflects one less scheduled slot for the course.
                    currentGenerator.generateOneSlotForCourse(courseName);

                    // Update the GUI and course input area
                    displaySchedule(currentGenerator.getCurrentSchedule(), currentGenerator.getTimeSlotsMap());
                    updateCourseInputArea(currentGenerator.getRemainingCourses());
                    
                    JOptionPane.showMessageDialog(this, 
                        String.format("The class '%s' has been deleted and successfully rescheduled to a new slot (if one was available).", courseName), 
                        "Reschedule Successful", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "Error deleting slot for reschedule.", "Reschedule Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please generate a timetable first.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * NEW: Updates the Course Input Area to reflect the current remaining slots after a deletion/update.
     */
    private void updateCourseInputArea(List<Course> remainingCourses) {
        StringBuilder sb = new StringBuilder();
        for (Course c : remainingCourses) {
            sb.append(String.format("%s, %s, %d\n", c.name, c.professor, c.requiredSlots));
        }
        courseInputArea.setText(sb.toString().trim());
    }

    /**
     * Parses the multi-line course input from the JTextArea.
     * Format: Name, Professor, Slots
     */
    private List<Course> parseCourses(String text) throws NumberFormatException {
        List<Course> courses = new ArrayList<>();
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) continue;

            String[] parts = trimmedLine.split(",");
            if (parts.length == 3) {
                String name = parts[0].trim();
                String professor = parts[1].trim();
                int slots = Integer.parseInt(parts[2].trim());
                if (!name.isEmpty() && !professor.isEmpty() && slots >= 0) { // Changed slots > 0 to slots >= 0
                    try {
                        // Course is accessible because it is package-private in TimetableModel.java (Canvas)
                        courses.add(new Course(name, professor, slots));
                    } catch (NoClassDefFoundError e) {
                        throw new RuntimeException("Missing or uncompiled TimetableModel.java (Course class not found).", e);
                    }
                }
            } else {
                 System.err.println("Skipping malformed course line: " + line);
            }
        }
        return courses;
    }

    /**
     * Parses the day and time slot strings into a map structure required by the generator.
     */
    private Map<String, List<String>> parseTimeSlots(String daysText, String timesText) {
        Map<String, List<String>> map = new LinkedHashMap<>(); // Use LinkedHashMap to maintain order
        
        List<String> days = Arrays.stream(daysText.split(","))
                                     .map(String::trim)
                                     .filter(s -> !s.isEmpty())
                                     .collect(Collectors.toList());
        
        List<String> times = Arrays.stream(timesText.split(","))
                                       .map(String::trim)
                                       .filter(s -> !s.isEmpty())
                                       .collect(Collectors.toList());
        
        if (!days.isEmpty() && !times.isEmpty()) {
            for (String day : days) {
                map.put(day, times);
            }
        }
        return map;
    }


    /**
     * Converts the List<ScheduleSlot> result into a formatted JTable display.
     */
    private void displaySchedule(List<ScheduleSlot> schedule, Map<String, List<String>> timeSlotsMap) {
        // 1. Determine the schedule matrix structure
        List<String> days = new ArrayList<>(timeSlotsMap.keySet());
        if (days.isEmpty()) return;
        List<String> times = timeSlotsMap.get(days.get(0)); // Assume all days have the same times

        // 2. Prepare the map for easy lookup: Day -> Time -> CourseName/Professor
        Map<String, Map<String, ScheduleSlot>> matrix = new HashMap<>();
        for (String day : days) {
            matrix.put(day, new HashMap<>());
        }
        
        for (ScheduleSlot slot : schedule) {
            if (matrix.containsKey(slot.day) && timeSlotsMap.get(slot.day).contains(slot.time)) {
                matrix.get(slot.day).put(slot.time, slot);
            }
        }

        // 3. Set the table columns: Time slots followed by Day names
        List<String> columns = new ArrayList<>();
        columns.add("Time/Day");
        columns.addAll(days);
        tableModel.setColumnIdentifiers(columns.toArray());
        tableModel.setRowCount(0); // Clear existing table data

        // 4. Fill the table row by row (one row per time slot)
        for (String time : times) {
            Object[] rowData = new Object[days.size() + 1];
            rowData[0] = time; // First column is the time slot
            
            for (int i = 0; i < days.size(); i++) {
                String day = days.get(i);
                ScheduleSlot slot = matrix.get(day).get(time);
                
                if (slot != null) {
                    // Display Course Name and Professor Name using HTML for formatting
                    rowData[i + 1] = "<html><b>" + slot.courseName + "</b><br>" + slot.professorName + "</html>";
                } else {
                    rowData[i + 1] = ""; // Empty slot
                }
            }
            tableModel.addRow(rowData);
        }
        
        // 5. Apply custom renderer for better display (uses the newly imported classes)
        scheduleTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                // Set default alignment
                setHorizontalAlignment(JLabel.CENTER);
                setVerticalAlignment(JLabel.CENTER);
                
                // Align content for the schedule columns (columns > 0)
                if (column > 0 && value.toString().contains("<html>")) {
                    setVerticalAlignment(JLabel.TOP); // Vertical align to top for multi-line HTML
                } 
                
                // Add a slight border/padding for better visual separation
                setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                
                // Change background color for selected slot or if it's the time column
                if (isSelected && column > 0) {
                     setBackground(new Color(255, 230, 230)); // Light red for selection
                } else if (column == 0) {
                     setBackground(new Color(240, 240, 240)); // Light gray for time column
                } else {
                    setBackground(Color.WHITE); // Default white
                }
                
                // Ensure text color is black
                setForeground(Color.BLACK);
                
                return this;
            }
        });
        
        scheduleTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    }

    public static void main(String[] args) {
        // Run the GUI creation on the Event Dispatch Thread (Swing standard practice)
        SwingUtilities.invokeLater(TimetableGUI::new);
    }
}
