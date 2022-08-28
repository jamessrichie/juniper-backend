package controller.userController;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;

import exceptions.NotYetImplementedException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import org.apache.commons.text.WordUtils;

import model.*;

@RestController
@RequestMapping("/user")
public class UserController {

	// Database controller
	private final Query database;

	// Email connection
	private final VerificationMailSender mailSender;

	// Password hashing parameter constants
    private static final int HASH_STRENGTH = 65536;
    private static final int KEY_LENGTH = 128;

	public UserController() throws SQLException, IOException {
		database = new Query();
		mailSender = new VerificationMailSender();
	}

	/**
	 * Creates a new user account
	 *
	 * TODO: ensure that the userHandle generated is unique. otherwise, generate a new 4 digit number
	 *
     * @url .../user/createUser?name=value1&email=value2&password=value3
	 * @return HTTP status code with message
	 */
	@PostMapping("/createUser")
	public ResponseEntity<String> createUser(@RequestParam(value = "name") String name,
											 @RequestParam(value = "email") String email,
											 @RequestParam(value = "password") String password) {

		name = WordUtils.capitalizeFully(name);
		email = email.toLowerCase();

		String userID = UUID.randomUUID().toString();
		String userHandle = name.replaceAll("\\s","").toLowerCase() + "#" + String.format("%04d", new Random().nextInt(10000));
		byte[] salt = get_salt();
		byte[] hash = get_hash(password, salt);
		String verificationCode = generateVerificationCode(64);

		// creates the user
		ResponseEntity<String> createUserStatus = database.transaction_createUser(userID, userHandle, name, email, salt, hash, verificationCode);
		if (createUserStatus.getStatusCode() != HttpStatus.OK) {
			return createUserStatus;
		}

		// on success, send verification email
		return mailSender.sendVerificationEmail(name, email, verificationCode);
	}

	/**
	 * Sends a verification email
	 *
	 * @url .../user/sendVerificationEmail?email=value1
	 * @return HTTP status code with message
	 */
	@PostMapping("/sendVerificationEmail")
	public ResponseEntity<String> sendVerificationEmail(@RequestParam(value = "email") String email) {

		email = email.toLowerCase();

		// gets the name of the user
		ResponseEntity<String> getNameStatus = database.transaction_getUserName(email);
		if (getNameStatus.getStatusCode() != HttpStatus.OK) {
			return getNameStatus;
		}
		String name = getNameStatus.getBody();

		// gets the verification code for the user
		ResponseEntity<String> getVerificationCodeStatus = database.transaction_getVerificationCode(email);
		if (getVerificationCodeStatus.getStatusCode() != HttpStatus.OK) {
			return getVerificationCodeStatus;
		}
		String verificationCode = getVerificationCodeStatus.getBody();

		// on success, send verification email
		return mailSender.sendVerificationEmail(name, email, verificationCode);

	}

	/**
	 * Verifies a user and redirects them to the login page
	 *
	 * @url .../user/verify?code=value1
	 * @return HTTP status code with message
	 */
	@GetMapping("/verifyUser")
	public ResponseEntity<String> verifyUser(@RequestParam(value = "code") String verificationCode) {

		ResponseEntity<String> verifyUserStatus = database.transaction_verifyUser(verificationCode);
		return verifyUserStatus;
	}

	/**
	 * Updates the login credentials of an existing user account
	 *
     * @url .../user/updateUserCredentials?userID=value1&password=value2&newPassword=value3
	 * @return
	 */
	@PostMapping("/updateUserCredentials")
	public Boolean updateUserCredentials(@RequestParam(value = "email") String email,
                                         @RequestParam(value = "new_email") String newEmail,
							 			 @RequestParam(value = "password") String password,
										 @RequestParam(value = "new_password") String newPassword) {

        throw new NotYetImplementedException();
	}

	/**
	 * Records changes to the user's profile
	 *
	 * @effect HTTP POST Request updates the model/database
	 * @return true iff operation succeeds
	 */
	@PostMapping(value = "/updateUserProfile", consumes = "application/json", produces = "application/json")
	public String updateUserProfile(@RequestBody String user, @RequestParam(value = "access_token") String accessToken) {

		// Overview
	 	// ––––––––––––––––––––––––––––––––––––––––––––––––––––––
	 	// assert that userID and password matches those stored
	 	// in the database before updating the associated profile
	 	// ––––––––––––––––––––––––––––––––––––––––––––––––––––––

		throw new NotYetImplementedException();
	}

	/**
     * Generates a random cryptographic salt
     *
     * @return cryptographic salt
     */
    private byte[] get_salt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return salt;
    }

	/**
     * Generates a cryptographic hash
     *
     * @param password password to be hashed
     * @param salt salt for the has
     * @return cryptographic hash
     */
    private byte[] get_hash(String password, byte[] salt) {
        // Specify the hash parameters
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_STRENGTH, KEY_LENGTH);

        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return factory.generateSecret(spec).getEncoded();

        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException();
        }
    }

	/**
	 * Generates a random string of specified length
	 */
	private String generateVerificationCode(int length) {
		int leftLimit = 97;
		int rightLimit = 122;

		Random random = new Random();
		StringBuilder buffer = new StringBuilder(length);

		for (int i = 0; i < length; i++) {
			int randomLimitedInt = leftLimit + (int) (random.nextFloat() * (rightLimit - leftLimit + 1));
			buffer.append((char) randomLimitedInt);
		}
		return buffer.toString();
	}
}
