import redis.clients.jedis.Jedis;
import java.util.Set;

public class find_cpp_repos {
    public static void main(String[] args) {
        Jedis jedis = new Jedis("localhost", 6379);
        jedis.select(0);
        
        Set<String> keys = jedis.keys("repo-*");
        System.out.println("Checking " + keys.size() + " repositories for C/C++ projects...\n");
        
        int count = 0;
        for (String key : keys) {
            String url = jedis.hget(key, "Url");
            if (url != null && (url.contains("rust") || url.contains("c") || url.contains("cpp") || 
                url.contains("llvm") || url.contains("gcc") || url.contains("clang") ||
                url.contains("opencv") || url.contains("tensorflow") || url.contains("bitcoin"))) {
                System.out.println("Repository ID: " + key);
                System.out.println("URL: " + url);
                System.out.println("---");
                count++;
                if (count >= 5) break;
            }
        }
        
        jedis.close();
    }
}

