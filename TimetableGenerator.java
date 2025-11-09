import java.util.*;
import java.util.stream.Collectors;


// 1. Data Structure for a Course (Package-private, NOT public)
class Course {
    final String name;
    final String professor;
    int requiredSlots; // Changed to NOT final to allow modifications (e.g., re-adding a slot)

    public Course(String name, String professor, int requiredSlots) {
        this.name = name;
        this.professor = professor;
        this.requiredSlots = requiredSlots;
    }

    @Override
    public String toString() {
        return name + " (" + professor + ", " + requiredSlots + " slots)";
    }
}

// 2. Data Structure for a Scheduled Slot (The final output) (Package-private, NOT public)
class ScheduleSlot {
    final String day;
    final String time;
    final String courseName;
    final String professorName;

    public ScheduleSlot(String day, String time, String courseName, String professorName) {
        this.day = day;
        this.time = time;
        this.courseName = courseName;
        this.professorName = professorName;
    }
    
    // Helper method for comparison
    public boolean matches(String day, String time, String courseName) {
        return this.day.equals(day) && this.time.equals(time) && this.courseName.equals(courseName);
    }
}

// 3. Core DSA Logic: Timetable Generator (Package-private, NOT public)
class TimetableGenerator {

    private final List<Course> courses;
    private final Map<String, Course> courseMap; // NEW: Map for easy lookup by name
    private final Map<String, List<String>> timeSlotsMap;
    private final int maxClassesPerDay;
    private final int minClassesPerDay;
    private final List<String> days;
    private final Random random = new Random();
    
    // NEW: Internal state of the generated schedule (made mutable)
    private List<ScheduleSlot> currentSchedule;
    // NEW: Internal state tracking for constraint checks
    private Map<String, Set<String>> professorOccupancy; // ProfessorName -> Set of "Day:Time"
    private Map<String, String> scheduleOccupancy;       // "Day:Time" -> CourseName
    private Map<String, Integer> dayOccupancy;          // Day -> Count

    /**
     * CONSTRUCTOR FIX: Now accepts four arguments, including minClassesPerDay.
     */
    public TimetableGenerator(List<Course> courses, Map<String, List<String>> timeSlotsMap, int maxClassesPerDay, int minClassesPerDay) {
        // Deep copy of courses to allow modification of requiredSlots in the local copy
        this.courses = courses.stream()
            .map(c -> new Course(c.name, c.professor, c.requiredSlots))
            .collect(Collectors.toList());
        this.courseMap = this.courses.stream()
            .collect(Collectors.toMap(c -> c.name, c -> c));
            
        this.timeSlotsMap = timeSlotsMap;
        this.maxClassesPerDay = maxClassesPerDay;
        this.minClassesPerDay = minClassesPerDay;
        this.days = new ArrayList<>(timeSlotsMap.keySet());
        
        // Initialize state variables for the generation/management logic
        this.currentSchedule = new ArrayList<>();
        this.professorOccupancy = new HashMap<>();
        this.scheduleOccupancy = new HashMap<>();
        this.dayOccupancy = timeSlotsMap.keySet().stream()
            .collect(Collectors.toMap(d -> d, d -> 0));
    }
    
    // NEW: Public getter for current schedule
    public List<ScheduleSlot> getCurrentSchedule() {
        return currentSchedule;
    }
    
    // NEW: Public getter for timeSlotsMap
    public Map<String, List<String>> getTimeSlotsMap() {
        return timeSlotsMap;
    }
    
    // NEW: Public getter for remaining courses/slots
    public List<Course> getRemainingCourses() {
        // Return a list of courses that still require or once required slots
        return this.courses;
    }

    /**
     * DSA Implementation: Greedy Constraint Satisfaction with Randomization
     * Attempts to fill the schedule by assigning the most constrained courses first.
     */
    public List<ScheduleSlot> generate() {
        // Map to track how many slots each course still needs
        Map<Course, Integer> remainingSlots = courses.stream()
            .collect(Collectors.toMap(c -> c, c -> c.requiredSlots));

        // Sort courses: Prioritizing courses that need more slots (Greedy choice)
        List<Course> sortedCourses = courses.stream()
                .sorted(Comparator.comparingInt(c -> -c.requiredSlots))
                .collect(Collectors.toList());

        boolean scheduleComplete = false;
        // Calculate max possible slots
        int totalPossibleSlots = timeSlotsMap.values().stream().mapToInt(List::size).sum(); 
        int maxIterations = totalPossibleSlots * 2; // Safety guard

        int iterationCount = 0;

        // Iteratively try to fill the remaining course slots
        while (!scheduleComplete && iterationCount < maxIterations) {
            scheduleComplete = true;

            // Create mutable and randomized copies of the days list
            List<String> randomizedDays = new ArrayList<>(days);
            Collections.shuffle(randomizedDays, random);

            for (Course course : sortedCourses) {
                if (remainingSlots.getOrDefault(course, 0) > 0) {
                    scheduleComplete = false; // Still work to do

                    // Try to find a valid slot for this course
                    boolean slotFound = tryScheduleOneSlot(course, randomizedDays, remainingSlots);
                    
                    if (slotFound) {
                        break; // Successfully scheduled one slot, restart the loop to process the next course
                    }
                }
            }
            
            // Check if all slots are filled for all courses
            if (remainingSlots.values().stream().mapToInt(i -> i).sum() == 0) {
                scheduleComplete = true;
            }
            iterationCount++;
        }

        if (!scheduleComplete) {
            System.err.println("Warning: Could not complete the schedule due to conflicting constraints!");
        }

        return currentSchedule; // Return the internal schedule state
    }
    
    /**
     * NEW: Attempts to schedule a single slot for a specific course (used for rescheduling).
     * @param courseName The name of the course to schedule.
     * @return true if a slot was found and scheduled, false otherwise.
     */
     public boolean generateOneSlotForCourse(String courseName) {
         Course course = courseMap.get(courseName);
         if (course == null) return false;
         
         // Temporarily set its remaining slot to 1 for generation
         Map<Course, Integer> remainingSlots = new HashMap<>();
         remainingSlots.put(course, 1);
         
         List<String> randomizedDays = new ArrayList<>(days);
         Collections.shuffle(randomizedDays, random);
         
         boolean slotFound = tryScheduleOneSlot(course, randomizedDays, remainingSlots);
         
         if (slotFound) {
             // Since we only needed to schedule one, we don't need to re-run the main loop
             return true;
         } else {
             // If not found, increment the required slot count back in the course object
             course.requiredSlots++; 
             return false;
         }
     }
     
     /**
      * Core logic to find and schedule one slot for a given course.
      */
     private boolean tryScheduleOneSlot(Course course, List<String> randomizedDays, Map<Course, Integer> remainingSlots) {
         for (String day : randomizedDays) {
             
             // 0. Max Classes Constraint Check
             if (dayOccupancy.get(day) >= maxClassesPerDay) {
                 continue;
             }

             // Get the times available for this specific day and randomize them
             List<String> times = timeSlotsMap.getOrDefault(day, Collections.emptyList());
             List<String> randomizedTimes = new ArrayList<>(times);
             Collections.shuffle(randomizedTimes, random);

             for (String time : randomizedTimes) {
                 String slotKey = day + ":" + time;

                 // 1. Slot Occupancy Check
                 if (scheduleOccupancy.containsKey(slotKey)) {
                     continue; 
                 }
                 
                 // 2. Contiguity Constraint (No Gaps) Check
                 int timeIndex = times.indexOf(time);
                 if (timeIndex > 0) {
                     String previousTime = times.get(timeIndex - 1);
                     String previousSlotKey = day + ":" + previousTime;
                     
                     // If this slot is not the first one available, the one before it MUST be occupied.
                     if (!scheduleOccupancy.containsKey(previousSlotKey)) {
                         continue;
                     }
                 } else if (timeIndex == 0 && dayOccupancy.get(day) > 0) {
                      // If it's the first time slot (index 0) but classes are already scheduled,
                      // something is wrong with the existing schedule (shouldn't happen with the first check)
                      // but this prevents scheduling if a gap was somehow created before the first slot.
                     // The logic is robust enough with the previous check, so this is mostly a guard.
                     continue;
                 }


                 // 3. Professor Availability Check
                 Set<String> occupiedSlots = professorOccupancy.computeIfAbsent(
                     course.professor, k -> new HashSet<>()
                 );
                 
                 // Check if professor is available AND try to reserve the slot
                 boolean isProfessorAvailable = occupiedSlots.add(slotKey);

                 if (isProfessorAvailable) {
                     // Slot is valid! Assign the course.
                     ScheduleSlot newSlot = new ScheduleSlot(day, time, course.name, course.professor);
                     currentSchedule.add(newSlot);
                     
                     // Update state variables
                     remainingSlots.put(course, remainingSlots.get(course) - 1);
                     course.requiredSlots--; // Decrement the required slots in the course object
                     scheduleOccupancy.put(slotKey, course.name);
                     dayOccupancy.put(day, dayOccupancy.get(day) + 1); 
                     
                     return true;
                 } 
             }
         }
         return false;
     }

    /**
     * NEW: Removes a scheduled slot and updates all internal tracking.
     * This makes the required slot available again.
     * @param day The day of the slot to delete.
     * @param time The time of the slot to delete.
     * @param courseName The course name of the slot to delete.
     * @return true if deletion was successful, false otherwise.
     */
    public boolean deleteSlot(String day, String time, String courseName) {
        
        // 1. Find and remove from the main schedule list
        boolean removedFromSchedule = currentSchedule.removeIf(slot -> slot.matches(day, time, courseName));

        if (removedFromSchedule) {
            String slotKey = day + ":" + time;
            Course course = courseMap.get(courseName);

            // 2. Remove from schedule occupancy map
            scheduleOccupancy.remove(slotKey);

            // 3. Remove from professor occupancy set
            if (course != null && professorOccupancy.containsKey(course.professor)) {
                professorOccupancy.get(course.professor).remove(slotKey);
            }

            // 4. Decrement day occupancy
            dayOccupancy.computeIfPresent(day, (k, v) -> v > 0 ? v - 1 : 0);
            
            // 5. Increment the required slots for the course
            if (course != null) {
                course.requiredSlots++;
            }
            
            return true;
        }
        return false;
    }
}
