package com.tamagotchi.restaurantclientapplication.services;

import com.tamagotchi.restaurantclientapplication.data.exceptions.AuthLoginException;
import com.tamagotchi.restaurantclientapplication.data.exceptions.AuthPasswordException;
import com.tamagotchi.restaurantclientapplication.data.model.LoginInfo;
import com.tamagotchi.tamagotchiserverprotocol.models.AuthenticateInfoModel;
import com.tamagotchi.tamagotchiserverprotocol.models.CredentialsModel;
import com.tamagotchi.tamagotchiserverprotocol.models.UserModel;
import com.tamagotchi.tamagotchiserverprotocol.routers.IAccountApiService;
import com.tamagotchi.tamagotchiserverprotocol.routers.IAuthenticateApiService;
import com.tamagotchi.tamagotchiserverprotocol.services.IAuthenticateInfoService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import retrofit2.HttpException;

public class AuthenticationService {

    private IAccountApiService accountApiService;
    private IAuthenticateApiService authenticateApiService;
    private IAuthenticateInfoService authenticateInfoService;
    private AuthenticationInfoStorageService storageService;

    private Observable<Boolean> isAuthenticatedSource;
    private BehaviorSubject<Boolean> isAuthenticatedSourceSubject;

    private BehaviorSubject<UserModel> currentUserSubject;
    private Observable<UserModel> currentUserSource;

    public Observable<Boolean> isAuthenticated() {
        return isAuthenticatedSource;
    }

    public Observable<UserModel> currentUser() {
        return currentUserSource;
    }

    private static final Object syncInstance = new Object();
    private static AuthenticationService instance;

    private AuthenticationService(IAuthenticateApiService authenticateApiService,
                          IAuthenticateInfoService authenticateInfoService,
                          IAccountApiService accountApiService,
                          AuthenticationInfoStorageService authenticationStorageService) {
        this.authenticateApiService = authenticateApiService;
        this.authenticateInfoService = authenticateInfoService;
        this.accountApiService = accountApiService;
        this.storageService = authenticationStorageService;

        isAuthenticatedSourceSubject = BehaviorSubject.create();
        currentUserSubject = BehaviorSubject.create();

        isAuthenticatedSource = isAuthenticatedSourceSubject.hide();
        currentUserSource = currentUserSubject.hide();

        this.loadAuthenticate();
    }

    public static AuthenticationService getInstance() {
        synchronized (syncInstance) {
            return instance;
        }
    }

    synchronized static void InitializeService(IAuthenticateApiService authenticateApiService, IAuthenticateInfoService authenticateInfoService, IAccountApiService accountApiService, AuthenticationInfoStorageService authenticationInfoStorageService) {
        synchronized (syncInstance) {
            instance = new AuthenticationService(authenticateApiService, authenticateInfoService, accountApiService, authenticationInfoStorageService);
        }
    }

    public void signOut() {
        storageService.removeToken();
        isAuthenticatedSourceSubject.onNext(false);
    }

    public Completable signIn(LoginInfo loginInfo) {
        CredentialsModel loginInfoModel = new CredentialsModel(loginInfo.getLogin(),
                loginInfo.getPassword().getPasswordMd5());

        // Сбрасываем предыдущее значение, т.к. оно вернеться подписчикам.
        authenticateInfoService.LogOut();

        return Completable.create(source -> {
            this.authenticateApiService.authenticate(loginInfoModel)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            accountAuthData -> {
                                // Если сервер не вернул jwt, но запрос был успешен, то нажно проверять сервер.
                                if (accountAuthData.getToken().isEmpty()) {
                                    source.onError(new Exception("Server fault. Check the server."));
                                    return;
                                }

                                // Сохраяем jwt и авторизируемся
                                authenticateInfoService.LogIn(new AuthenticateInfoModel(accountAuthData.getToken()));
                                storageService.saveToken(accountAuthData.getToken());
                                loadAuthenticate();

                                source.onComplete();
                            },
                            error -> {
                                if (error instanceof HttpException) {
                                    HttpException httpError = (HttpException) error;

                                    switch (httpError.code()) {
                                        case 401:
                                            source.onError(new AuthPasswordException());
                                            break;
                                        case 404:
                                            source.onError(new AuthLoginException());
                                            break;
                                        default:
                                            source.onError(new Exception(error));
                                    }
                                } else {
                                    source.onError(new Exception(error));
                                }
                            });
        });
    }

    private void loadAuthenticate() {

        if (!authenticateInfoService.isAuthenticate()) {
            String token = storageService.getToken();
            if (!token.isEmpty()) {
                authenticateInfoService.LogIn(new AuthenticateInfoModel(token));
            }
        }

        if (authenticateInfoService.isAuthenticate()) {
            this.accountApiService.getCurrentAccount()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            account -> {
                                isAuthenticatedSourceSubject.onNext(true);
                                currentUserSubject.onNext(account);
                            }
                            , error -> {
                                authenticateInfoService.LogOut();
                                signOut();
                            }
                    );
        } else {
            isAuthenticatedSourceSubject.onNext(false);
        }
    }
}
