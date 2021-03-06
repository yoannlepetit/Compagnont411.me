package fr.lepetitpingouin.android.t411;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

public class Torrent {

    public String name;
    public String id;
    public String url;
    public String category="";
    public String size, uploader, age, seeders, leechers, avis, complets, ratioa, ratiob;
    public static String STATUS_PENDING = "(P)";
    public static String STATUS_BANNED = "(B)";
    public static String STATUS_VOTE = "(V)";
    public static String STATUS_NEW = "(N)";
    public static String STATUS_VALID = "(A)";
    private Context context;
    private SharedPreferences prefs;
    private torrentFileGetter tDL;
    private AsyncDlLater dll;
    private AsyncDlLaterNot dllNot;

    private JSONArray json;

    public Torrent(Context context, String name, String id) {
        this.context = context.getApplicationContext();
        this.name = name;
        this.id = id;
        this.url = Default.URL_GET_TORRENT + id;
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public Torrent(Context context, String name, String id, String size, String uploader, String category) {
        this.context = context.getApplicationContext();
        this.name = name;
        this.id = id;
        this.size = size;
        this.category = category;
        this.uploader = uploader;
        this.url = Default.URL_GET_TORRENT + id;
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            this.json = new JSONArray(prefs.getString("jsonTorrentList", "[]"));
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    public void download() {
        tDL = new torrentFileGetter();
        try {
            tDL.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void bookmark() {
        dll = new AsyncDlLater();
        try {
            dll.execute();
        } catch (Exception e) {
        }
    }

    public void unbookmark() {
        dllNot = new AsyncDlLaterNot();
        try {
            dllNot.execute();
        } catch (Exception e) {
        }
    }

    public void delete() {
        new T411Logger(context).writeLine("Suppression du torrent "+getTorrentName());
        File file = new File(getTorrentPath(), getTorrentName());
        if(file.exists()) {
            file.delete();
        }
        JSONArray newJson = new JSONArray();
        Boolean deleted = false;
        for(int i = 0; i < json.length(); i++) {

            try {
                Log.e("json ", json.get(i).toString() + "/ID+ "+this.id);
                if(deleted || !((JSONObject)json.get(i)).get("id").equals(this.id)) {
                    newJson.put(json.get(i));
                } else {
                    deleted = true;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        prefs.edit().putString("jsonTorrentList",newJson.toString()).commit();
    }

    public void share() {

        String url = Default.URL_SHARE;
        if (prefs.getBoolean("shareDirectLink", false))
            url = Default.URL_GET_PREZ;

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, url + this.id + "\n\n-\n" + context.getString(R.string.shareSignature));
        share.putExtra(Intent.EXTRA_SUBJECT, "[t411] " + this.name);

        context.startActivity(Intent.createChooser(share, context.getString(R.string.Share)).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void doNotify(int icon, String title, String subtitle, int id, PendingIntent pendingIntent) {
        try {
            if (pendingIntent == null)
                pendingIntent = PendingIntent.getActivity(context, 0, new Intent(), 0);

            NotificationCompat.Builder mBuilder =
                    new NotificationCompat.Builder(context)
                            .setSmallIcon(icon)
                            .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), new CategoryIcon(category).getIcon()))
                            .setContentTitle(title)
                            .setContentText(subtitle);

            if(icon == R.drawable.ic_notif_torrent_done) {
                Intent dlIntent = new Intent(context.getApplicationContext(), TorrentsListActivity.class);
                PendingIntent dlpI = PendingIntent.getActivity(context.getApplicationContext(), 0, dlIntent, 0);
                mBuilder.addAction(R.drawable.ic_downloads, "Téléchargements", dlpI);
            }

            mBuilder.setContentIntent(pendingIntent);
            mBuilder.setAutoCancel(true);
            NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(id, mBuilder.build());

        } finally {
            try {
                Toast.makeText(this.context, subtitle, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
            }
        }
    }

    private void cancelNotify(int id) {
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(id);
    }

    public void launchUrl() {
        Intent i = new Intent();
        i.setAction(android.content.Intent.ACTION_VIEW);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.setData(Uri.parse(Default.URL_GET_PREZ+this.id));
        context.startActivity(i);
    }

    public void open() {
        Intent i = new Intent();
        i.setAction(android.content.Intent.ACTION_VIEW);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        File file = new File(this.getTorrentPath(), this.getTorrentName());
        i.setDataAndType(Uri.fromFile(file), "application/x-bittorrent");


        context.startActivity(i);
    }

    private class AsyncDlLaterNot extends AsyncTask<Void, String[], Void> {

        String msg = "";

        @Override
        protected Void doInBackground(Void... arg0) {
            String username = prefs.getString("login", ""), password = prefs
                    .getString("password", "");

            Connection.Response res = null;
            Document doc = null;

            try {
                res = Jsoup
                        .connect(Default.URL_LOGIN)
                        .data("login", username, "password", password)
                        .method(Connection.Method.POST)
                        .userAgent(prefs.getString("User-Agent", Default.USER_AGENT))
                        .timeout(Integer.valueOf(prefs.getString("timeoutValue", Default.timeout)) * 1000)
                        .maxBodySize(0).followRedirects(true).ignoreContentType(true)
                        .execute();

                Map<String, String> Cookies = res.cookies();

                res = Jsoup
                        .connect(Default.URL_UNBOOKMARK)
                        .cookies(Cookies)
                        .data("id", "", "submit", "Supprimer", "ids[]", id)
                        .timeout(Integer.valueOf(prefs.getString("timeoutValue", Default.timeout)) * 1000)
                        .maxBodySize(0).followRedirects(true).ignoreContentType(true)
                        .userAgent(prefs.getString("User-Agent", Default.USER_AGENT))
                        .method(Connection.Method.POST)
                        .execute();

                doc = res.parse();

                msg = doc.select("div#messages").first().text();

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (!msg.equals(""))
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        }

    }

    private class AsyncDlLater extends AsyncTask<Void, String[], Void> {

        String msg;

        @Override
        protected Void doInBackground(Void... arg0) {
            String username = prefs.getString("login", ""), password = prefs.getString("password", "");

            Connection.Response res;
            Document doc;

            try {

                res = Jsoup
                        .connect(Default.URL_LOGIN)
                        .data("login", username, "password", password)
                        .method(Connection.Method.POST)
                        .userAgent(prefs.getString("User-Agent", Default.USER_AGENT))
                        .timeout(Integer.valueOf(prefs.getString("timeoutValue", Default.timeout)) * 1000)
                        .maxBodySize(0).followRedirects(true).ignoreContentType(true)
                        .execute();

                Map<String, String> Cookies = res.cookies();

                res = Jsoup
                        .connect(Default.URL_BOOKMARK + id)
                        .cookies(Cookies)
                        .data("login", username, "password", password)
                        .method(Connection.Method.POST)
                        .userAgent(prefs.getString("User-Agent", Default.USER_AGENT))
                        .timeout(Integer.valueOf(prefs.getString("timeoutValue", Default.timeout)) * 1000)
                        .maxBodySize(0).followRedirects(true).ignoreContentType(true).ignoreHttpErrors(true)
                        .ignoreContentType(true).execute();

                doc = res.parse();

                //doc = Jsoup.parse(new SuperT411HttpBrowser(context).login(username, password).connect(Default.URL_BOOKMARK + id).executeInAsyncTask());

                //Log.e("bookmark", doc.body().toString());
                msg = doc.select("div.fade").first().text();

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        }

    }

    private class torrentFileGetter extends AsyncTask<Void, String[], Void> {

        Connection.Response resTorrent;
        byte[] torrentFileContent;
        String torrentContent;

        @Override
        protected void onPreExecute() {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                doNotify(R.drawable.ic_notif_torrent_failure, name, "Permissions nécessaires", Integer.valueOf(id), null);
            }

            doNotify(R.drawable.ic_notif_torrent_downloading, name, "Téléchargement...", Integer.valueOf(id), null);
            new T411Logger(context).writeLine("Initialisation du téléchargement");
        }

        @Override
        protected Void doInBackground(Void... arg0) {

            String username = prefs.getString("login", ""), password = prefs.getString("password", "");

            try {

                boolean proxy = prefs.getBoolean("usePaidProxy", false);
                new T411Logger(context).writeLine(proxy?"Proxy dédié actif":"Proxy dédié inactif");

                /*

                new T411Logger(context).writeLine("Connexion par login/password pour " + username);
                Connection.Response res = Jsoup
                        .connect(Default.URL_LOGIN)
                        .data("login", username, "password", password)
                        .method(Connection.Method.POST)
                        .userAgent(prefs.getString("User-Agent", Default.USER_AGENT))
                        .timeout(Integer.valueOf(prefs.getString("timeoutValue", Default.timeout)) * 1000)
                        .maxBodySize(0).followRedirects(true).ignoreContentType(true)
                        .execute();

                Map<String, String> Cookies = res.cookies();

                new T411Logger(context).writeLine("Lecture du fichier torrent...");


                resTorrent = Jsoup.connect(Default.URL_GET_TORRENT + id)
                        .data("login", username, "password", password)
                        .method(Connection.Method.POST)
                        .maxBodySize(0).followRedirects(true).ignoreContentType(true)
                        .userAgent(prefs.getString("User-Agent", Default.USER_AGENT))
                                //.timeout(prefs.getInt("timeoutValue", Default.timeout) * 1000)
                        .cookies(Cookies)
                        .execute();
                        torrentFileContent = resTorrent.bodyAsBytes();
                        */
                SuperT411HttpBrowser browser = new SuperT411HttpBrowser(context)
                        .connect(Default.URL_GET_TORRENT + id)
                        .login(username, password);
                torrentContent = browser.executeInAsyncTask();

                torrentFileContent = browser.getByteResponse();

            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {

            new T411Logger(context).writeLine("Ecriture du fichier torrent...");

            File file = new File(getTorrentPath(), getTorrentName());
            file.setWritable(true, false);

            try {
                json.put(new JSONObject("{'title':'"+name+"','uploader':'"+uploader+"','size':'"+size+"','id':'"+id+"', 'url':'"+url+"', 'category':'"+category+"'}"));
                prefs.edit().putString("jsonTorrentList",json.toString()).apply();
            } catch(Exception ex) {
                ex.printStackTrace();
            }

            try {
                file.createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {

                FileOutputStream fo = new FileOutputStream(file);
                fo.write(torrentFileContent);
                fo.close();

                /*OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(file));
                osw.write(torrentContent);
                osw.close();*/

                Intent i = new Intent();
                i.setAction(android.content.Intent.ACTION_VIEW);
                //i.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton().getMimeTypeFromExtension("torrent"));
                if (prefs.getBoolean("addMimeType", false))
                    i.setDataAndType(Uri.fromFile(file), "application/x-bittorrent");
                else //auto-detect
                    //i.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.getName().substring(file.getName().lastIndexOf(".")+1)));
                    i.setData(Uri.fromFile(file));

                PendingIntent pI = PendingIntent.getActivity(context, 0, Intent.createChooser(i, context.getResources().getString(R.string.open_with_app)), PendingIntent.FLAG_UPDATE_CURRENT);
                //doNotify(R.drawable.ic_notif_torrent_done, name, "Téléchargement terminé !", Integer.valueOf(id), pI);
                doNotify(R.drawable.ic_notif_torrent_done, name, "Téléchargement terminé !", Integer.valueOf(id), pI);
                if (prefs.getBoolean("openAfterDl", false)) {
                    //ouvrir le fichier
                    try {
                        context.getApplicationContext().startActivity(i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | PendingIntent.FLAG_UPDATE_CURRENT | Intent.FLAG_FROM_BACKGROUND));
                        if (prefs.getBoolean("openAfterDlCancelNotify", false))
                            cancelNotify(Integer.valueOf(id));
                    } catch (Exception e) {
                        doNotify(R.drawable.ic_notif_torrent_failure, name, "Erreur d'ouverture du torrent\nAucune application trouvée.", Integer.valueOf(id), null);
                    }
                }

            } catch (IOException e) {
                Intent i = new Intent();
                i.setClass(context, UserPrefsActivity.class);
                PendingIntent pI = PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
                doNotify(R.drawable.ic_notif_torrent_failure, name, "Le téléchargement a échoué...\nAccès au répertoire choisi impossible.", Integer.valueOf(id), pI);
                e.printStackTrace();
                new T411Logger(context).writeLine("Accès au répertoire choisi impossible : " + prefs.getString("filePicker", Environment.getExternalStorageDirectory().getPath()));
            } catch (Exception e) {
                Intent i = new Intent();
                i.setClass(context, UserPrefsActivity.class);
                PendingIntent pI = PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
                if (file.exists() && file.length() == 0) {
                    doNotify(R.drawable.ic_notif_torrent_failure, name, "Le téléchargement a échoué...\nErreur réseau : impossible de télécharger le contenu du fichier. Veuillez réessayer.", Integer.valueOf(id), pI);
                    e.printStackTrace();
                    new T411Logger(context).writeLine("Impossible de lire le contenu du fichier", T411Logger.ERROR);
                } else if (!file.exists()) {
                    doNotify(R.drawable.ic_notif_torrent_failure, name, "Le téléchargement a échoué...\nImpossible de créer le fichier.", Integer.valueOf(id), pI);
                } else {
                    doNotify(R.drawable.ic_notif_torrent_failure, name, "Le téléchargement a échoué...\nErreur inconnue.", Integer.valueOf(id), pI);
                }
            }
        }
    }

    public String getTorrentPath() {
        return prefs.getString("filePicker", Environment.getExternalStorageDirectory().getPath());
    }

    public String getTorrentName() {
        return name.replaceAll("/", "_") + ".torrent";
    }
}
