package types;

public class UserMini {

    public String userId;
    public String userHandle;
    public String userName;
    public String profilePictureUrl;

    public UserMini(String userId, String userHandle, String userName, String profilePictureUrl) {
        this.userId = userId;
        this.userHandle = userHandle;
        this.userName = userName;
        this.profilePictureUrl = profilePictureUrl;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("UserMini {\n");
        sb.append("    userId: ")           .append(userId)           .append("\n");
        sb.append("    userHandle: ")       .append(userHandle)       .append("\n");
        sb.append("    userName: ")         .append(userName)         .append("\n");
        sb.append("    profilePictureUrl: ").append(profilePictureUrl).append("\n");
        sb.append("}");
        return sb.toString();
    }
}
