import cn.hutool.crypto.digest.BCrypt;

public class GeneratePasswords {
    public static void main(String[] args) {
        // Generate correct BCrypt hashes for test users
        String[] passwords = {"20260325", "123", "321", "1", "admin", "123456", "xdx", "btwo", "666", "wusuowei", "1788", "20050329"};
        
        for (String pwd : passwords) {
            String hash = BCrypt.hashpw(pwd, BCrypt.gensalt());
            System.out.println("Password: " + pwd + " -> Hash: " + hash + " (length: " + hash.length() + ")");
        }
    }
}
