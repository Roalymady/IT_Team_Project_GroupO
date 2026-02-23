package structures;

// This class is currently unused but provides a structure for user credentials
public class User {

    // Protected field for the user's login name
    protected String username;
    
    // Protected field for the user's password
    protected String password;

    // Default constructor
    public User() {
    }

    // Constructor with parameters to initialize a user
    public User(String username, String password) {
        super(); // Call to the parent Object constructor
        this.username = username; // Assign the username
        this.password = password; // Assign the password
    }

    // Returns the username of the user
    public String getUsername() {
        return username; // Return the username field
    }

    // Updates the username of the user
    public void setUsername(String username) {
        this.username = username; // Set the username field
    }

    // Returns the password of the user
    public String getPassword() {
        return password; // Return the password field
    }

    // Updates the password of the user
    public void setPassword(String password) {
        this.password = password; // Set the password field
    }
}