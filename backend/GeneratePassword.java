import cn.hutool.crypto.digest.BCrypt;

public class GeneratePassword {
    public static void main(String[] args) {
        // 生成常用密码的BCrypt哈希值
        String[] passwords = {"admin", "123", "123456", "password", "test"};
        
        for (String pwd : passwords) {
            String hash = BCrypt.hashpw(pwd, BCrypt.gensalt());
            System.out.println("密码: " + pwd + " -> 哈希: " + hash + " (长度: " + hash.length() + ")");
        }
    }
}
