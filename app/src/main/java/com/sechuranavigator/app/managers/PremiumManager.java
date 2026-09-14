// archivo: app/src/main/java/com/sechuranavigator/app/managers/PremiumManager.java
package com.sechuranavigator.app.managers;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.sechuranavigator.app.ErrorLogger;

import java.util.ArrayList;
import java.util.List;

public class PremiumManager implements PurchasesUpdatedListener {

    private static final String TAG = "PremiumManager";

    // IDs deben coincidir EXACTAMENTE con los de Google Play Console
    public static final String PRODUCT_MONTHLY = "pro_mensual";
    public static final String PRODUCT_YEARLY  = "pro_anual";

    private final Context       context;
    private BillingClient       billingClient;
    private boolean             isPremium = false;
    private OnPremiumListener   listener;

    // Caché local de los detalles de productos (para mostrar precios)
    private List<ProductDetails> productDetailsList = new ArrayList<>();

    public PremiumManager(Context context) {
        this.context = context.getApplicationContext();
        setupBillingClient();
    }

    // ── Setup ──────────────────────────────────────────────────────────────

    private void setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases()
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult result) {
                if (result.getResponseCode() ==
                        BillingClient.BillingResponseCode.OK) {
                    ErrorLogger.log("BillingClient conectado");
                    checkExistingPurchases();
                    loadProductDetails();
                } else {
                    ErrorLogger.log("BillingClient error: " +
                            result.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Se intentará reconectar automáticamente
                ErrorLogger.log("BillingClient desconectado");
            }
        });
    }

    // ── Verificar compras existentes ───────────────────────────────────────

    /**
     * Verifica si el usuario ya tiene una suscripción activa.
     * Llamar al inicio de la app.
     */
    public void checkExistingPurchases() {
        billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                (billingResult, purchases) -> {
                    if (billingResult.getResponseCode() !=
                            BillingClient.BillingResponseCode.OK) return;

                    isPremium = false;
                    for (Purchase purchase : purchases) {
                        if (purchase.getPurchaseState() ==
                                Purchase.PurchaseState.PURCHASED) {
                            isPremium = true;
                            // Confirmar la compra si no se confirmó antes
                            if (!purchase.isAcknowledged()) {
                                acknowledgePurchase(purchase);
                            }
                            break;
                        }
                    }

                    // Guardar en SharedPreferences para acceso offline
                    context.getSharedPreferences("sechura_settings",
                                    Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("is_premium", isPremium)
                            .apply();

                    ErrorLogger.log("isPremium: " + isPremium);
                    if (listener != null) listener.onPremiumStatusChanged(isPremium);
                }
        );
    }

    // ── Cargar detalles de productos (precios) ─────────────────────────────

    private void loadProductDetails() {
        List<QueryProductDetailsParams.Product> products = new ArrayList<>();
        products.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build());
        products.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build());

        billingClient.queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder()
                        .setProductList(products)
                        .build(),
                (billingResult, productDetailList) -> {
                    if (billingResult.getResponseCode() ==
                            BillingClient.BillingResponseCode.OK) {
                        this.productDetailsList = productDetailList;
                        ErrorLogger.log("Productos cargados: " +
                                productDetailList.size());
                    }
                }
        );
    }

    // ── Lanzar flujo de compra ─────────────────────────────────────────────

    /**
     * Abre la pantalla de suscripción de Google Play.
     * @param activity Activity actual (necesaria para el flujo de pago)
     * @param productId PRODUCT_MONTHLY o PRODUCT_YEARLY
     */
    public void launchPurchaseFlow(Activity activity, String productId) {
        ProductDetails product = findProduct(productId);
        if (product == null) {
            ErrorLogger.log("Producto no encontrado: " + productId);
            return;
        }

        // Obtener la oferta de suscripción
        List<ProductDetails.SubscriptionOfferDetails> offerDetails =
                product.getSubscriptionOfferDetails();
        if (offerDetails == null || offerDetails.isEmpty()) return;

        List<BillingFlowParams.ProductDetailsParams> paramsBuilder =
                new ArrayList<>();
        paramsBuilder.add(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offerDetails.get(0).getOfferToken())
                        .build()
        );

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(paramsBuilder)
                .build();

        BillingResult result =
                billingClient.launchBillingFlow(activity, flowParams);

        if (result.getResponseCode() !=
                BillingClient.BillingResponseCode.OK) {
            ErrorLogger.log("Error lanzando billing: " +
                    result.getDebugMessage());
        }
    }

    // ── Callback de compra completada ──────────────────────────────────────

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult,
                                   List<Purchase> purchases) {
        if (billingResult.getResponseCode() !=
                BillingClient.BillingResponseCode.OK || purchases == null) {
            return;
        }

        for (Purchase purchase : purchases) {
            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                isPremium = true;
                if (!purchase.isAcknowledged()) {
                    acknowledgePurchase(purchase);
                }
                context.getSharedPreferences("sechura_settings",
                                Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("is_premium", true)
                        .apply();

                if (listener != null) listener.onPurchaseSuccess();
                ErrorLogger.log("Compra exitosa");
            }
        }
    }

    private void acknowledgePurchase(Purchase purchase) {
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams
                .newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();
        billingClient.acknowledgePurchase(params,
                result -> ErrorLogger.log("Compra confirmada: " +
                        result.getResponseCode()));
    }

    // ── API pública ────────────────────────────────────────────────────────

    /**
     * Verifica si el usuario es premium.
     * Primero verifica en memoria, luego en SharedPreferences (para offline).
     */
    public boolean isPremium() {
        if (isPremium) return true;
        // Fallback: verificar en SharedPreferences (puede estar desactualizado)
        return context.getSharedPreferences("sechura_settings",
                        Context.MODE_PRIVATE)
                .getBoolean("is_premium", false);
    }

    /**
     * Obtiene el precio formateado de un producto para mostrar en la UI.
     * Ejemplo: "S/ 5.00/mes"
     */
    public String getPrice(String productId) {
        ProductDetails product = findProduct(productId);
        if (product == null) return "—";
        List<ProductDetails.SubscriptionOfferDetails> offers =
                product.getSubscriptionOfferDetails();
        if (offers == null || offers.isEmpty()) return "—";
        List<ProductDetails.PricingPhase> phases =
                offers.get(0).getPricingPhases().getPricingPhaseList();
        if (phases.isEmpty()) return "—";
        return phases.get(0).getFormattedPrice();
    }

    public void setListener(OnPremiumListener listener) {
        this.listener = listener;
    }

    public void destroy() {
        if (billingClient != null) billingClient.endConnection();
    }

    // ── Utilidades ─────────────────────────────────────────────────────────

    private ProductDetails findProduct(String productId) {
        for (ProductDetails pd : productDetailsList) {
            if (pd.getProductId().equals(productId)) return pd;
        }
        return null;
    }

    // ── Interfaz listener ──────────────────────────────────────────────────

    public interface OnPremiumListener {
        void onPremiumStatusChanged(boolean isPremium);
        void onPurchaseSuccess();
    }
}