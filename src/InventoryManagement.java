import java.awt.*;
import java.util.*;

public class InventoryManagement {

    private ArrayList<Shelf> shelves;
    private HashMap<String, InventoryItem> skulookup;
    private HashMap<InventoryItem, Shelf> currentInventory;
    private Master master;

    /**
     * constructor - declares a null inventory list and and shelves list to be used to manage inventory.
     */
    public InventoryManagement(Master master) {
        this.master = master;
        shelves = new ArrayList<>();
        // Add 5 shelves
        for (int i = 0; i < 5; i++) {
            addShelf(new Point(100+20*i, 70));
        }
        currentInventory = new HashMap<>();
        skulookup = new HashMap<>();
    }

    /**
     *
     * @param location - takes the location of where the shelf will be placed
     * description: creates a shelf at the given location defined by the parameter location and adds it to the list of
     *              all shelves.
     *
     */
    public void addShelf(Point location) {
        Shelf shelf = new Shelf(UUID.randomUUID(), location);
        shelves.add(shelf);
    }

    /**
     *
     * @param item - item to be added to a shelf
     * description: takes an item given as a parameter and selects a shelf for it to be placed on.
     */
    public void addItem(InventoryItem item) {
            skulookup.put(item.getId(), item);
            int idx = new Random().nextInt(shelves.size());
            addItem(item, shelves.get(idx));
    }

    /**
     *
     * @param item - item to be added to a shelf
     * @param shelf - specific shelf that the given item should be added to
     * description: adds an item to a specific shelf and indicates this addition in the currentInventory HashMap.
     */
    public void addItem(InventoryItem item, Shelf shelf) {
        currentInventory.put(item, shelf);
    }

    public HashMap<InventoryItem, Shelf> getCurrentInventory() {
        return currentInventory;
    }

    /**
     *
     * @param item - single item to be used in generating an order
     *
     * Description: This is used to call the robot with the specific items that need to be taken
     *              to the belt by the robot.
     */
    public void generateOrder(InventoryItem item){
        //TODO:Figure out order process and implement inventory accordingly
    }


    /**
     *
     * @param item - item to be found
     * @return the shelf object the item is on
     * description: takes an item object and finds the current shelf it is on and returns it.
     */
    public Shelf getItemShelf(InventoryItem item) {
            return currentInventory.get(item);
    }

    public InventoryItem getItembySku(String sku) {
        return skulookup.get(sku);
    }


    public Shelf getShelfByLocation(Point location) {
        for (Shelf s : shelves) {
            if (s.getLocation().equals(location)) {
                return s;
            }
        }
        return null;
    }



}

