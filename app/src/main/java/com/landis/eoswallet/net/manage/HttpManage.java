package com.landis.eoswallet.net.manage;

import android.content.Context;
import android.support.annotation.Nullable;

import com.blankj.utilcode.util.LogUtils;
import com.landis.eoswallet.base.constant.Constant;
import com.landis.eoswallet.net.api.ApiService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class HttpManage {

    public static ApiService apiService;
    private static OkHttpClient okHttpClient;

    public static void init() {
        apiService = new Retrofit.Builder()
                .baseUrl(Constant.BASE_URL)
                .client(getOkHttp())
                .callFactory(new CallFactoryProxy(okHttpClient) {
                    @Nullable
                    @Override
                    protected HttpUrl getNewUrl(String baseUrlName, Request request) {
                        if (baseUrlName.equals("json_bin")) {
                            String oldUrl = request.url().toString();
                            String newUrl = oldUrl.replace(Constant.BASE_URL, Constant.JSON_BIN_BASE_URL);
                            LogUtils.d("??????url"+newUrl);
                            return HttpUrl.get(URI.create(newUrl));
                        }
                        return null;
                    }
                })
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(ApiService.class);
    }


    private static OkHttpClient getOkHttp() {
        if (okHttpClient == null) {
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .addInterceptor(HttpInterceptor.requestBuilderInterceptor())
                    .proxy(Proxy.NO_PROXY)
                    .retryOnConnectionFailure(true)
                    .readTimeout(20, TimeUnit.SECONDS);
            clientBuilder.sslSocketFactory(getSLLContext().getSocketFactory());
            SSLParams sslSocketFactoryBase = getSslSocketFactoryBase(null, null, null);
            clientBuilder.sslSocketFactory(sslSocketFactoryBase.sSLSocketFactory, sslSocketFactoryBase.trustManager);
            clientBuilder.hostnameVerifier((hostname, session) -> true);
            okHttpClient = clientBuilder.build();
        }
        return okHttpClient;
    }

    /**
     * ??????????????????
     *
     * @return
     */
    public static SSLContext getSLLContext() {
        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, new SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
        return sslContext;
    }

    public static SSLContext getSLLContext(Context context) {
        SSLContext sslContext = null;
        try {
            String crt = "-----BEGIN CERTIFICATE-----\n" +
                    "-----END CERTIFICATE-----\n";
            CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");

            InputStream cerInputStream = new ByteArrayInputStream(crt.getBytes());
            Certificate ca = cf.generateCertificate(cerInputStream);

            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);

            String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            tmf.init(keyStore);
            TrustManager[] trustManagers = tmf.getTrustManagers();

            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, null);
        } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException | IOException e) {
            e.printStackTrace();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        }
        return sslContext;
    }

    //    public static Picasso getPicasso(Context c) {
//        return HttpsPicasso.PicassoHolder.getInstance(getOkHttp(c), c);
//    }
    public static class SSLParams {
        public SSLSocketFactory sSLSocketFactory;
        public X509TrustManager trustManager;
    }


    private static SSLParams getSslSocketFactoryBase(X509TrustManager trustManager, InputStream bksFile, String password, InputStream... certificates) {
        SSLParams sslParams = new SSLParams();
        try {
            KeyManager[] keyManagers = prepareKeyManager(bksFile, password);
            TrustManager[] trustManagers = prepareTrustManager(certificates);
            X509TrustManager manager;
            if (trustManager != null) {
                //??????????????????????????????TrustManager
                manager = trustManager;
            } else if (trustManagers != null) {
                //?????????????????????TrustManager
                manager = chooseTrustManager(trustManagers);
            } else {
                //????????????????????????TrustManager
                manager = UnSafeTrustManager;
            }
            // ??????TLS?????????SSLContext????????? that uses our TrustManager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            // ??????????????????trustManagers?????????SSLContext?????????sslContext????????????keyStore????????????
            // ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            sslContext.init(keyManagers, new TrustManager[]{manager}, null);
            // ??????sslContext??????SSLSocketFactory??????
            sslParams.sSLSocketFactory = sslContext.getSocketFactory();
            sslParams.trustManager = manager;
            return sslParams;
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        } catch (KeyManagementException e) {
            throw new AssertionError(e);
        }
    }

    private static KeyManager[] prepareKeyManager(InputStream bksFile, String password) {
        try {
            if (bksFile == null || password == null) return null;
            KeyStore clientKeyStore = KeyStore.getInstance("BKS");
            clientKeyStore.load(bksFile, password.toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(clientKeyStore, password.toCharArray());
            return kmf.getKeyManagers();
        } catch (Exception e) {
        }
        return null;
    }

    private static TrustManager[] prepareTrustManager(InputStream... certificates) {
        if (certificates == null || certificates.length <= 0) return null;
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            // ???????????????????????????KeyStore??????????????????????????????
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null);
            int index = 0;
            for (InputStream certStream : certificates) {
                String certificateAlias = Integer.toString(index++);
                // ???????????????????????????????????????????????? cert
                Certificate cert = certificateFactory.generateCertificate(certStream);
                // ??? cert ???????????????????????????keyStore???
                keyStore.setCertificateEntry(certificateAlias, cert);
                try {
                    if (certStream != null) certStream.close();
                } catch (IOException e) {
                }
            }
            //?????????????????????????????????TrustManagerFactory
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            //??????????????????keyStore???????????????TrustManagerFactory?????????tmf????????????keyStore????????????
            tmf.init(keyStore);
            //??????tmf??????TrustManager?????????TrustManager????????????keyStore????????????
            return tmf.getTrustManagers();
        } catch (Exception e) {
        }
        return null;
    }

    private static X509TrustManager chooseTrustManager(TrustManager[] trustManagers) {
        for (TrustManager trustManager : trustManagers) {
            if (trustManager instanceof X509TrustManager) {
                return (X509TrustManager) trustManager;
            }
        }
        return null;
    }

    /**
     * ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
     * ??????????????????????????????????????????
     */
    public static X509TrustManager UnSafeTrustManager = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[]{};
        }
    };
}
