package com.tamagotchi.tamagotchiserverprotocol;

import com.tamagotchi.tamagotchiserverprotocol.routers.IAccountsApiService;
import com.tamagotchi.tamagotchiserverprotocol.routers.IAuthenticateApiService;
import com.tamagotchi.tamagotchiserverprotocol.services.AuthenticateInfoService;
import com.tamagotchi.tamagotchiserverprotocol.services.IAuthenticateInfoService;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Класс для работы с сервером. Является singleton.
 */
public class RestaurantClient {
    private static RestaurantClient instance = null;
    private Retrofit retrofit;
    private OkHttpClient client;
    private String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6Miwicm9sZSI6Ik1hbmFnZXIiLCJpYXQiOjE1ODY5NzEyNTV9.lasbKegsStSGla3JY3dsIJLmQ2PriyGDh9kA8xA9Jds";

    private IAccountsApiService accountsService;
    IAuthenticateApiService authenticateService;
    private AuthenticateInfoService authenticateInfoService = new AuthenticateInfoService();

    /**
     * Инициализация retrofit клиента.
     */
    private RestaurantClient() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {

                        // Добавляем JWT токен в запрос для аутентификации.
                        Request.Builder newRequest = chain.request().newBuilder();
                        if (authenticateInfoService.isAuthenticate()) {
                            newRequest.addHeader("Authorization", "Bearer " + token);
                        }

                        return chain.proceed(newRequest.build());
                    }
                })
                .build();

        // TODO: что то нужно сделать с жестко заданным URL.
        String BASE_URL = "http://192.168.56.1:3000/api/";

        // Собираем retrofit клиент для отпрвки запросов на сервер.
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        // Инициализируем маршрутизаторы retrofit
        accountsService = retrofit.create(IAccountsApiService.class);
        authenticateService = retrofit.create(IAuthenticateApiService.class);
    }

    public synchronized static RestaurantClient getInstance() {
        if (instance == null) {
            instance = new RestaurantClient();
        }

        return instance;
    }

    /**
     * Предоставляет сервис для работы с пользователями системы.
     * @return /api/user service
     */
    public IAccountsApiService getAccountService() {
        return accountsService;
    }

    /**
     * Предоставляет сервис для работы с авторизацией системы.
     * @return /api/authenticate service
     */
    public IAuthenticateApiService getAuthenticateService() { return authenticateService; }

    /**
     * Предоставляет сервис для работы с данными авторизации.
     * Большенство запросов не будут работать без их установки.
     * Для получения данных аутентификации используйте {@linkplain IAccountsApiService
     * AccountsApiService}
     * @return /api/user service
     */
    public IAuthenticateInfoService getAuthenticateInfoService() {
        return authenticateInfoService;
    }


}
