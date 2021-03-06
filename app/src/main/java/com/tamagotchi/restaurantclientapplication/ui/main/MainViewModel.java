package com.tamagotchi.restaurantclientapplication.ui.main;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.tamagotchi.restaurantclientapplication.Application;
import com.tamagotchi.restaurantclientapplication.data.Result;
import com.tamagotchi.restaurantclientapplication.data.model.FullMenuItem;
import com.tamagotchi.restaurantclientapplication.data.model.OrderVisitInfo;
import com.tamagotchi.restaurantclientapplication.data.repositories.DishesRepository;
import com.tamagotchi.restaurantclientapplication.data.repositories.FeedbackRepository;
import com.tamagotchi.restaurantclientapplication.data.repositories.MenuRepository;
import com.tamagotchi.restaurantclientapplication.data.repositories.OrderRepository;
import com.tamagotchi.restaurantclientapplication.data.repositories.RestaurantsRepository;
import com.tamagotchi.restaurantclientapplication.data.repositories.UsersRepository;
import com.tamagotchi.restaurantclientapplication.services.AuthenticationService;
import com.tamagotchi.tamagotchiserverprotocol.models.FeedbackCreateModel;
import com.tamagotchi.tamagotchiserverprotocol.models.OrderCreateModel;
import com.tamagotchi.tamagotchiserverprotocol.models.OrderModel;
import com.tamagotchi.tamagotchiserverprotocol.models.RestaurantModel;
import com.tamagotchi.tamagotchiserverprotocol.models.UpdatableInfoUser;
import com.tamagotchi.tamagotchiserverprotocol.models.UserModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MainViewModel extends ViewModel {

    private static final String LogTag = "MainViewModel";

    /**
     * Репозиторий ресторанов.
     */
    private RestaurantsRepository restaurantsRepository;

    /**
     * Репозиторий меню.
     */
    private MenuRepository menuRepository;

    /**
     * Сервис для работы с аутинтификацией пользователя.
     */
    private AuthenticationService authenticationService;

    /**
     * Репозиторий для отзывов.
     */
    private OrderRepository orderRepository;

    /**
     * Репозиторий для пользователя.
     */
    private UsersRepository usersRepository;

    /**
     * Репозиторий для заказов.
     */
    private FeedbackRepository feedbackRepository;

    /**
     * Репозиторий для блюд.
     */
    private DishesRepository dishesRepository;

    /**
     * Выбранный элемент навигации приложения (нижняя панель)
     */
    private MutableLiveData<Navigation> selectedNavigation = new MutableLiveData<>(Navigation.Restaurant);

    /**
     * Все ресторы в системе.
     */
    private MutableLiveData<Result<List<RestaurantModel>>> restaurants = new MutableLiveData<>();

    /**
     * Информация о посещении выбранного ресторана.
     */
    private MutableLiveData<OrderVisitInfo> orderVisitInfo = new MutableLiveData<>();

    /**
     * Выбранный ресторан.
     */
    private MutableLiveData<RestaurantModel> selectedRestaurant = new MutableLiveData<>();

    /**
     * Меню выбранного ресторана.
     */
    private MutableLiveData<Result<List<FullMenuItem>>> selectedRestaurantMenu = new MutableLiveData<>();

    /**
     * Элементы меню, которые выбрал пользователь.
     */
    private MutableLiveData<List<FullMenuItem>> userMenuSubject = new MutableLiveData<>();

    /**
     * Все заказы пользователя.
     */
    private MutableLiveData<List<OrderModel>> allUserOrders = new MutableLiveData<>();

    /**
     * Текущий пользователь.
     */
    private MutableLiveData<UserModel> currentUser = new MutableLiveData<>();

    /**
     * Subscriber для завершенных заказов.
     */
    private Disposable completedUserSubscriber;

    private Disposable completedOrdersSubscriber;

    private Disposable menuItemRequest = null;

    private List<FullMenuItem> userMenu = new ArrayList<>();

    MainViewModel(RestaurantsRepository restaurantsRepository, DishesRepository dishesRepository,
                  MenuRepository menuRepository, AuthenticationService authenticationService,
                  OrderRepository orderRepository, FeedbackRepository feedbackRepository, UsersRepository usersRepository) {
        Application.startWorking();
        this.feedbackRepository = feedbackRepository;
        this.restaurantsRepository = restaurantsRepository;
        this.dishesRepository = dishesRepository;
        this.menuRepository = menuRepository;
        this.authenticationService = authenticationService;
        this.orderRepository = orderRepository;
        this.usersRepository = usersRepository;
        InitRestaurants();
        InitOrderVisitInfo();
        InitUser();
    }

    private void InitUser() {
        completedUserSubscriber = this.authenticationService.currentUser().subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        currentUser -> this.currentUser.setValue(currentUser),
                        RuntimeException::new
                );
    }

    private void InitOrderVisitInfo() {
        Calendar visitTime = Calendar.getInstance();
        visitTime.add(Calendar.HOUR, 1);
        orderVisitInfo.setValue(new OrderVisitInfo(visitTime, 1));
    }


    private void initSubscribeAllOrders() {
        UserModel currentUser = this.currentUser.getValue();
        if (currentUser != null) {
            completedOrdersSubscriber = this.orderRepository.getUserOrders(currentUser.getId())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            orders -> this.allUserOrders.setValue(orders),
                            error -> {
                            }
                    );
        } else {
            Log.e(LogTag, "initSubscribeAllOrders: User not initialize");
        }
    }

    public LiveData<UserModel> getUser() {
        return currentUser;
    }

    public void setUserName(String userName) {
        UserModel currentUser = this.currentUser.getValue();

        UpdatableInfoUser updatableInfoUser = new UpdatableInfoUser(null, currentUser.getRole(),
                userName, null, currentUser.getAvatar(), null);

        if (completedUserSubscriber != null) {
            completedUserSubscriber.dispose();
        }

        completedUserSubscriber = this.usersRepository.updateUser(currentUser.getId(), updatableInfoUser).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        user -> this.currentUser.setValue(user),
                        error -> {
                            Log.e(LogTag, "Can't update user info", error);
                        });
    }

    public void logOut() {
        authenticationService.signOut();
    }

    public void sendFeedback(String feedback) {
        FeedbackCreateModel feedbackCreateModel = new FeedbackCreateModel(feedback);
        feedbackRepository.addFeedback(feedbackCreateModel)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(success -> {
                    // Отправлен, нужно оповещение пользователя
                }, error -> {
                    Log.e(LogTag, "Can't send feedback", error);
                });
    }

    public void refreshOrders() {
        allUserOrders.setValue(new ArrayList<>());

        if (completedOrdersSubscriber != null) {
            completedOrdersSubscriber.dispose();
        }

        this.initSubscribeAllOrders();
    }

    public LiveData<List<OrderModel>> getUserOrders() {
        return allUserOrders;
    }

    public LiveData<Result<List<RestaurantModel>>> getRestaurants() {
        return restaurants;
    }

    public LiveData<RestaurantModel> getSelectedRestaurant() {
        return selectedRestaurant;
    }

    public void setSelectedRestaurant(RestaurantModel restaurant) {
        selectedRestaurant.setValue(restaurant);
        InitRestaurantMenu(restaurant);
    }

    /**
     * Возвращает observable на элементы меню, которые выбрал пользователь.
     *
     * @return LiveData меню пользователя.
     */
    public LiveData<List<FullMenuItem>> getUserMenu() {
        return userMenuSubject;
    }

    /**
     * Добавить новый элемент меню в пользовательеское меню.
     *
     * @param menuItem новый элемент меню.
     */
    public void addToUserMenu(FullMenuItem menuItem) {
        userMenu = new ArrayList<>(userMenu);
        userMenu.add(menuItem);
        userMenuSubject.setValue(userMenu);
    }

    /**
     * Удалить элемент из пользовательского меню.
     *
     * @param menuItem удаляемый элемент.
     */
    public void removeFromUserMenu(FullMenuItem menuItem) {
        userMenu = new ArrayList<>(userMenu);
        userMenu.remove(menuItem);
        userMenuSubject.setValue(userMenu);
    }

    /**
     * Очистить меню пользователя.
     */
    public void clearUserMenu() {
        userMenu = new ArrayList<>(userMenu);
        userMenu.clear();
        userMenuSubject.setValue(userMenu);
    }

    /**
     * Возвращает observable на меню ресторана.
     *
     * @return LiveData на меню ресторана.
     */
    public LiveData<Result<List<FullMenuItem>>> getSelectedRestaurantMenu() {
        return selectedRestaurantMenu;
    }

    private void setSelectedRestaurantMenu(Result<List<FullMenuItem>> restaurantMenu) {
        selectedRestaurantMenu.setValue(restaurantMenu);
    }

    public LiveData<OrderVisitInfo> getOrderVisitInfo() {
        return orderVisitInfo;
    }

    public void setOrderVisitInfo(OrderVisitInfo visitInfo) {
        orderVisitInfo.setValue(visitInfo);
    }

    private void InitRestaurants() {
        this.restaurantsRepository.getAllRestaurants()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        restaurants -> {
                            this.restaurants.setValue(new Result.Success(restaurants));
                        },
                        error -> {
                            this.restaurants.setValue(new Result.Error(new Exception(error)));
                        }
                );
    }

    /**
     * Выполняем иницилизацию меню выбранного ресторана, для этого:
     * 1. Получаем коллекцию MenuItem с сервера.
     * 2. По каждому MenuItem запрашивем Dish, который ему принадлежит.
     * 3. Формируем FullMenuItem из Dish и MenuItem, делаем коллекцию из элементов и
     * отправляем в LiveData.
     * TODO: возможно стоит убрать возврат коллекций из репозиториев, но это довольно сложно.
     *
     * @param restaurant ресторан, меню которого требуется инициализировать.
     */
    private void InitRestaurantMenu(RestaurantModel restaurant) {
        // Отписываемся от предыдущей подписка. Она нам больше не нужна, если ресторан был сменен.
        if (menuItemRequest != null) {
            menuItemRequest.dispose();
        }

        // Очищаем меню пользователя.
        clearUserMenu();

        menuItemRequest = this.menuRepository.getMenu(restaurant.getId())
                .toObservable()
                .subscribeOn(Schedulers.io())
                .flatMap(
                        list -> Observable.fromIterable(list)
                                .flatMap(
                                        menuItem -> this.dishesRepository.getDishById(menuItem.getDishId()).toObservable()
                                                .subscribeOn(Schedulers.io())
                                                .onErrorResumeNext(
                                                        x -> {
                                                            Log.e(LogTag, x.toString());
                                                            return Observable.empty();
                                                        })
                                                .map(
                                                        dishModel ->
                                                                new FullMenuItem(menuItem, dishModel)
                                                )))
                .toList()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        menuItems -> {
                            this.setSelectedRestaurantMenu(new Result.Success(menuItems));
                        }, error -> {
                            this.setSelectedRestaurantMenu(new Result.Error(new Exception(error)));
                        });
    }

    /**
     * Установить текущую навигацию приложения.
     *
     * @param navigation выбранный элемент меню.
     */
    public void setSelectedNavigation(Navigation navigation) {
        selectedNavigation.setValue(navigation);
    }

    /**
     * Возвращает observable на выбранный пользователем элемент навигации.
     * Используется для обработки переключения навигации.
     *
     * @return observable
     */
    public LiveData<Navigation> getSelectedNavigation() {
        return selectedNavigation;
    }

    public Completable doOrder() {
        return orderRepository.createOrder(buildOrderInfo(null));
    }

    public Completable doOrder(String paymentToken) {
        return orderRepository.createOrder(buildOrderInfo(paymentToken));
    }

    private OrderCreateModel buildOrderInfo(String paymentToken) {
        List<Integer> orderMenu = new ArrayList<>();
        for (FullMenuItem menuItem :
                userMenu) {
            orderMenu.add(menuItem.getId());
        }

        if (orderMenu.size() == 0) {
            orderMenu = null;
        }

        Calendar timeVisitCalendar = orderVisitInfo.getValue().getVisitTime();
        SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(timeVisitCalendar.getTimeZone());
        // Убираем секунды и миллисекунды из заказа
        // TODO: сделать это на сервере (на текущий момент это заняло бы больше времени, чем поставить тут)
        timeVisitCalendar.set(Calendar.SECOND, 0);
        timeVisitCalendar.set(Calendar.MILLISECOND, 0);
        String timeVisit = sdf.format(timeVisitCalendar.getTime());

        return new OrderCreateModel(getSelectedRestaurant().getValue().getId(),
                currentUser.getValue().getId(),
                orderMenu,
                orderVisitInfo.getValue().getNumberOfVisitors(),
                null,
                paymentToken,
                timeVisit);
    }
}
