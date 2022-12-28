package types;

import java.util.*;

public class User {

    public String userId;
    public String userHandle;
    public String userName;
    public String cardColor;
    public String dateOfBirth;
    public String universityName;
    public String major;
    public String standing;
    public String gpa;
    public String biography;
    public String profilePictureUrl;
    public String numberOfFriends;
    public String rating;

    public List<String> mediaUrls;
    public List<String> courseCodes;

    public User(String userId, String userHandle, String userName,
                String cardColor, String dateOfBirth, String universityName,
                String major, String standing, String gpa, String biography,
                String profilePictureUrl, String numberOfFriends, String rating,
                List<String> mediaUrls, List<String> courseCodes) {

        this.userId = userId;
        this.userHandle = userHandle;
        this.userName = userName;
        this.cardColor = cardColor;
        this.dateOfBirth = dateOfBirth;
        this.universityName = universityName;
        this.major = major;
        this.standing = standing;
        this.gpa = gpa;
        this.biography = biography;
        this.profilePictureUrl = profilePictureUrl;
        this.numberOfFriends = numberOfFriends;
        this.rating = rating;
        this.mediaUrls = mediaUrls;
        this.courseCodes = courseCodes;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("User {\n");
        sb.append("    userId: ")           .append(userId)           .append("\n");
        sb.append("    userHandle: ")       .append(userHandle)       .append("\n");
        sb.append("    userName: ")         .append(userName)         .append("\n");
        sb.append("    cardColor: ")        .append(cardColor)        .append("\n");
        sb.append("    dateOfBirth: ")      .append(dateOfBirth)      .append("\n");
        sb.append("    universityName: ")   .append(universityName)   .append("\n");
        sb.append("    major: ")            .append(major)            .append("\n");
        sb.append("    standing: ")         .append(standing)         .append("\n");
        sb.append("    gpa: ")              .append(gpa)              .append("\n");
        sb.append("    biography: ")        .append(biography)        .append("\n");
        sb.append("    profilePictureUrl: ").append(profilePictureUrl).append("\n");
        sb.append("    numberOfFriends: ")  .append(numberOfFriends)  .append("\n");
        sb.append("    rating: ")           .append(rating)           .append("\n");
        sb.append("    mediaUrls: ")        .append(mediaUrls)        .append("\n");
        sb.append("    courseCodes: ")      .append(courseCodes)      .append("\n");
        sb.append("}");
        return sb.toString();
    }
}
