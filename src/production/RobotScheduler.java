package production;

import java.awt.*;
import java.util.*;

/**
 * Class that controls the robots
 * @author Jacob Roschen
 *
 */
public class RobotScheduler implements EventConsumer {
    LinkedList<Robot> availableRobots = new LinkedList<>();
    ArrayList<Robot> chargingRobots = new ArrayList<>();
    ArrayList<Robot> workingRobots = new ArrayList<>();
    // Used for path finding
    private PriorityQueue<Cell> openCells;
    private boolean closedCells[][];

    Master master;
    Floor floor;

    /**
     * Creates the RobotScheduler along with the robots it controls. By default, it creates 10 robots
     * @author Jacob Roschen
     *
     * @param m Master Object
     * @param f Floor Object
     */
    RobotScheduler(Master master, Floor floor) {
        this.master = master;
        this.floor = floor;
        seedRobots(10);
    }

    /**
     * Initializes Robot objects in the warehouse
     * @author Jacob Roschen
     *
     * @param robotCount Number of robots to initialize
     */
    void seedRobots(int robotCount) {
        for (int i = 0; i < robotCount; i++) {
            Point position = new Point(20 + i, 0);
            Robot robot = new Robot(i, master, floor, position);
            availableRobots.add(robot);
        }
    }

    /**
     * Handles the events 'AvailableRobotRetrieveFromLocation', 'SpecificRobotPlotPath', and 'EndItemRetrieval'
     *
     * @author Jacob Roschen
     * @author Wes Weirather
     *
     * @param task
     * @param event
     */
    @Override
    public void handleTaskEvent(Task task, Event event) {
        switch (task.type) {
            case AvailableRobotRetrieveFromLocation:
                master.printTime();
                System.out.println("Sending a robot to [" + task.location.x + "," + task.location.y + "]");

                Robot robot = getNextRobot();
                if(robot == null) {
                    event.addFirstTask(task, this);
                    master.scheduleEvent(event, 1);
                    return;
                }

                // Finish the item retrieval
                event.addFirstTask(new Task(Task.TaskType.EndItemRetrieval, robot, null), this);
                // Direct the robot back to its home
                event.addFirstTask(new Task(Task.TaskType.SpecificRobotPlotPath, robot, robot.getLocation()), this);
                // Lower the shelf
                event.addFirstTask(new Task(Task.TaskType.LowerShelf), robot);
                // Direct the robot and its shelf back to the shelf's home
                event.addFirstTask(new Task(Task.TaskType.SpecificRobotPlotPath, robot, task.location), this); // Go back to the shelf area
                // Tell the picker the item has arrived
                event.addFirstTask(new Task(Task.TaskType.PickItemFromShelf, null, task.item), master.picker);
                // Direct the robot to the picker
                event.addFirstTask(new Task(Task.TaskType.SpecificRobotPlotPath, robot, master.picker.getDropoffLocation()), this);
                // Tell the robot to get the shelf
                event.addFirstTask(new Task(Task.TaskType.RaiseShelf), robot);
                // Direct the robot to the shelf with the item
                event.addFirstTask(new Task(Task.TaskType.SpecificRobotPlotPath, robot, task.location), this);
                master.scheduleEvent(event);
                break;
            case SpecificRobotPlotPath:
                ArrayList<Point> route = findPath(task.robot.getLocation(), task.location, task.robot.hasShelf());
                if (route == null) {
                    System.out.println("Robot is already at destination or a path cannot be determined");
                    master.scheduleEvent(event, 1);
                    return;
                }
                // Add events to head of event ticket in reverse order (They end up in the same order)
                for (int i=route.size()-1; i>=0; i--) {
                    event.addFirstTask(new Task(Task.TaskType.SpecificRobotToLocation, route.get(i)), task.robot);
                }
                master.scheduleEvent(event);
                break;
            case EndItemRetrieval:
                // After the robot has been returned home, let it do other work including start charging
                chargingRobots.add(task.robot);
                workingRobots.remove(task.robot);
                // Let the order event progress
                master.scheduleEvent(event);
                // Spawn a new event to charge so order can complete independently
                Event spawnedevent = new Event(new Task(Task.TaskType.RobotCharge, task.robot), this);
                master.scheduleEvent(spawnedevent);
                break;
            case RobotCharge:
                task.robot.charge();
                System.out.println("Charging robot "+ task.robot + " " + task.robot.chargeLevel() +"%");
                if(task.robot.needsRecharge()) {
                    // keep charging
                    event.addFirstTask(new Task(Task.TaskType.RobotCharge, task.robot), this);
                    master.scheduleEvent(event, 1);
                } else {
                    // Done charging
                    System.out.println("Robot "+ task.robot +" done charging");
                    availableRobots.add(task.robot);
                    chargingRobots.remove(task.robot);
                }
                break;
        }
    }

    /**
     * Calculates the cost of the move from the current cell to the next one. Is a helper method for findPath()
     * @author Jacob Roschen
     *
     * @param current The current cell
     * @param next The cell you want to move to
     * @param hasShelf Does the robot have a shelf?
     */
    private void checkAndUpdateCost(Cell current, Cell next, boolean hasShelf) {
        if (!floor.canMove(next, hasShelf) || closedCells[next.x][next.y]) return;
        int nextFinalCost = next.heuristicCost + current.finalCost + 1;

        boolean inOpen = openCells.contains(next);
        if (!inOpen || nextFinalCost < next.finalCost) {
            next.finalCost = nextFinalCost;
            next.parent = current;
            if (!inOpen) openCells.add(next);
        }
    }

    /**
     * Finds a path from the start Point to the end Point using the A* algorithm
     * @author Jacob Roschen
     *
     * @param start Starting Point
     * @param end Ending Point
     * @param hasShelf Does the robot currently have a shelf?
     * @return An ArrayList of the coordinates that the robot needs to traverse
     */
    ArrayList<Point> findPath(Point start, Point end, boolean hasShelf) {
        ArrayList<Point> path = new ArrayList<>();

        Cell[][] grid = floor.getGrid();
        closedCells = new boolean[grid.length][grid[0].length];
        openCells = new PriorityQueue<>((Object o1, Object o2) -> {
            Cell c1 = (Cell) o1;
            Cell c2 = (Cell) o2;

            return c1.finalCost < c2.finalCost ? -1 :
                    c1.finalCost > c2.finalCost ? 1 : 0;
        });

        for (int i = 0; i < grid.length; ++i) {
            for (int j = 0; j < grid[0].length; ++j) {
                grid[i][j].finalCost = 0;
                grid[i][j].parent = null;
                grid[i][j].heuristicCost = Math.abs(i - end.x) + Math.abs(j - end.y);
            }
        }

        openCells.add(grid[start.x][start.y]);

        Cell curLoc;
        while ((curLoc = openCells.poll()) != null) {
            closedCells[curLoc.x][curLoc.y] = true;

            if (curLoc.equals(grid[end.x][end.y])) {
                break;
            }

            Cell t;
            Point[] neighbors = {
                    new Point(curLoc.x - 1, curLoc.y),
                    new Point(curLoc.x, curLoc.y - 1),
                    new Point(curLoc.x, curLoc.y + 1),
                    new Point(curLoc.x + 1, curLoc.y)
            };

            for (Point neighbor : neighbors) {
                if (neighbor.x < 0 || neighbor.y < 0 || neighbor.y >= grid[0].length || neighbor.x >= grid.length)
                    continue;

                t = grid[neighbor.x][neighbor.y];
                checkAndUpdateCost(curLoc, t, hasShelf);
            }
        }


        if (closedCells[end.x][end.y]) {
            //Trace back the path
            Cell current = grid[end.x][end.y];
            path.add(current);
            while (current.parent != null) {
                current = current.parent;
                path.add(0, current);
            }
            // The current location is at the front of the list, remove it
            path.remove(0);
        } else {
            return null;
        }

        return path;
    }

    /**
     * Gets the next available robot
     * If the return value is null, then no free robots were available
     * @author Jacob Roschen
     *
     * @return The next available robot, or null
     */
    Robot getNextRobot() {
        try {
            Robot r = availableRobots.removeFirst();
            workingRobots.add(r);
            return r;
        } catch (NoSuchElementException ex) {
            // No available robots to fetch the order
            System.out.println("No robots available: deferring");
            return null;
        }
    }
}
