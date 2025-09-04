package com.zeywox.veyronixcore.pre_test;


import com.zeywox.veyronixcore.http.utils.ProductDataUtil;
import org.junit.jupiter.api.Disabled;

import java.net.http.HttpClient;
import java.time.Duration;

@Disabled("Skip in build")
public class Test1 {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String CATEGORY = "Computer";

    private static final int TIMEOUT_SEC = Integer.getInteger("bench.timeoutSec", 10);

    private static HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SEC))
            .build();

    private static int STOCK_VALUE = Integer.getInteger("bench.stockValue", 7);

    private static String PRODUCT_ID = ProductDataUtil.ensureTargetProductId(
            BASE_URL, CATEGORY, client, Duration.ofSeconds(TIMEOUT_SEC), STOCK_VALUE);

    static {
        System.out.println();
        System.out.println("productId: "+PRODUCT_ID);
    }



    public static void main(String[] args) {

    }

}
