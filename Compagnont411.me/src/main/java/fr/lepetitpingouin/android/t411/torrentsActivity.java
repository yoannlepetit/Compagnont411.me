package fr.lepetitpingouin.android.t411;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class torrentsActivity extends AppCompatActivity {
    private String connectUrl;
    private String searchTerms;
    private String name = null;

    private String id;

    private HashMap<String, String> itemMmap;

    private ProgressDialog dialog;

    private SharedPreferences prefs;
    private Handler handler;

    private String order;
    private String type;
    private String tx_order;

    private torrentFetcher mF;

    private HashMap<String, String> map;

    private ImageButton prev;
    private ImageButton next;
    private LinearLayout dropdown_pages;
    private LinearLayout navbar;
    private LinearLayout dropdown_categories;

    private String strNext;
    private String strPrev;
    private String paginator;
    private String pageName;

    private Dialog pages_dialog;
    private Dialog cat_dialog;

    private GridView maListViewPerso;
    private ListView PagesList;
    private ListView catList;
    private ArrayList<HashMap<String, String>> listItem;
    private ArrayList<HashMap<String, String>> pageListItem;
    private ArrayList<HashMap<String, String>> catListItem;

    private SimpleAdapter mSchedule;
    private SimpleAdapter mTorrentsAdapter;

    @Override
    public void onDestroy() {
        mF = null;
        super.onDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_torrentslist);

        new T411Logger(getApplicationContext()).writeLine("Lancement d'une recherche de torrent");

        ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connManager.getActiveNetworkInfo();
        if (!(netInfo != null && netInfo.isConnectedOrConnecting())) {
            Intent i = new Intent(Default.Appwidget_update);
            i.putExtra("LED_T411", true);
            i.putExtra("LED_Net", false);
            sendBroadcast(i);

            Toast.makeText(getApplicationContext(), getString(R.string.noConError), Toast.LENGTH_SHORT).show();
            finish();
        }

        handler = new Handler();

        connectUrl = getIntent().getStringExtra("url");
        searchTerms = getIntent().getStringExtra("keywords");

        new T411Logger(getApplicationContext()).writeLine("Recherche lancée : " + searchTerms);

        order = getIntent().getStringExtra("order");
        new T411Logger(getApplicationContext()).writeLine("Ordre de tri : " + order);
        type = getIntent().getStringExtra("type");
        new T411Logger(getApplicationContext()).writeLine("Type : " + type);

        tx_order = getIntent().getStringExtra("tx_order");

        setSupportActionBar((Toolbar)findViewById(R.id.toolbar));
        getSupportActionBar().setTitle(searchTerms);

        if (tx_order != null)
            getSupportActionBar().setSubtitle(tx_order + ", " + type);

        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        navbar = (LinearLayout) findViewById(R.id.navbar);
        navbar.setVisibility(View.GONE);

        dropdown_categories = (LinearLayout) findViewById(R.id.category_filter);
        dropdown_categories.setVisibility(getIntent().getStringExtra("sender").contains("top") ? View.VISIBLE : View.GONE);
        dropdown_categories.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cat_dialog.show();
            }
        });

        cat_dialog = new Dialog(this, R.style.MyDialogTheme);
        cat_dialog.setContentView(R.layout.dialog_listview);
        cat_dialog.setTitle("Filtrer...");

        catList = (ListView) cat_dialog.findViewById(R.id.dialoglistview);
        catListItem = new ArrayList<HashMap<String, String>>();

        catList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            @SuppressWarnings("unchecked")
            public void onItemClick(AdapterView<?> a, View v, int position, long id) {
                HashMap<String, String> map = (HashMap<String, String>) catList
                        .getItemAtPosition(position);

                TextView tv = (TextView) findViewById(R.id.tvCatListFilter);
                tv.setText(map.get("name"));
                ImageView iv = (ImageView) findViewById(R.id.ivCatListFilter);
                iv.setImageResource(Integer.valueOf(map.get("icon")));
                cat_dialog.dismiss();
                switchList(map.get("code"));
            }
        });

        pages_dialog = new Dialog(this, R.style.MyDialogTheme);
        pages_dialog.setContentView(R.layout.dialog_listview);
        pages_dialog.setTitle("Aller à...");

        PagesList = (ListView) pages_dialog.findViewById(R.id.dialoglistview);
        pageListItem = new ArrayList<HashMap<String, String>>();

        PagesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            @SuppressWarnings("unchecked")
            public void onItemClick(AdapterView<?> a, View v, int position, long id) {
                HashMap<String, String> map = (HashMap<String, String>) PagesList
                        .getItemAtPosition(position);

                TextView tv = (TextView) findViewById(R.id.navbar_pagesText);
                tv.setText(map.get("name"));
                paginator = map.get("code");
                pages_dialog.dismiss();
                update();
            }
        });

        prev = (ImageButton) findViewById(R.id.navbtn_prev);
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                paginator = strPrev;
                update();
            }
        });

        next = (ImageButton) findViewById(R.id.navbtn_next);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                paginator = strNext;
                update();
            }
        });

        dropdown_pages = (LinearLayout) findViewById(R.id.navbtn_list);
        dropdown_pages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pages_dialog.show();
            }
        });

        prev.setVisibility(View.INVISIBLE);
        next.setVisibility(View.INVISIBLE);

        maListViewPerso = (GridView) findViewById(R.id.malistviewperso);
        listItem = new ArrayList<HashMap<String, String>>();

        id = getIntent().getStringExtra("id");

        maListViewPerso.setOnItemClickListener(new OnItemClickListener() {
            @Override
            @SuppressWarnings("unchecked")
            public void onItemClick(AdapterView<?> a, View v, int position,
                                    long id) {
                HashMap<String, String> map = (HashMap<String, String>) maListViewPerso.getItemAtPosition(position);

                Intent i;
                if (!prefs.getBoolean("dlModeRedirect", false)) {
                    i = new Intent();
                    i.setClass(getApplicationContext(), torrentDetailsActivity.class);
                    i.putExtra("url", Default.URL_GET_PREZ + map.get("ID"));
                    i.putExtra("nom", map.get("nomComplet"));
                    i.putExtra("ID", map.get("ID"));
                    i.putExtra("icon", Integer.valueOf(map.get("icon")));
                    i.putExtra("DlLater", connectUrl.equals(Default.URL_BOOKMARKS));
                } else {
                    i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(Default.URL_GET_PREZ.replace("torrents/torrents/", "torrents/") + map.get("ID")));
                }
                startActivity(i);

            }
        });
        registerForContextMenu(maListViewPerso);
        update();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.malistviewperso) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.torrent_context_menu, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        itemMmap = (HashMap<String, String>) maListViewPerso.getItemAtPosition(info.position);
        switch (item.getItemId()) {
            case R.id.torrent_context_menu_open:

                Intent i;
                if (!prefs.getBoolean("dlModeRedirect", false)) {
                    i = new Intent();
                    i.setClass(getApplicationContext(), torrentDetailsActivity.class);
                    i.putExtra("url", Default.URL_GET_PREZ + itemMmap.get("ID"));
                    i.putExtra("nom", itemMmap.get("nomComplet"));
                    i.putExtra("ID", itemMmap.get("ID"));
                    i.putExtra("icon", Integer.valueOf(itemMmap.get("icon")));
                    i.putExtra("DlLater", connectUrl.equals(Default.URL_BOOKMARKS));
                } else {
                    i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(Default.URL_GET_PREZ.replace("torrents/torrents/", "torrents/") + itemMmap.get("ID")));
                }
                startActivity(i);

                return true;
            case R.id.torrent_context_menu_download:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    if(shouldShowRequestPermissionRationale(android.Manifest.permission.READ_EXTERNAL_STORAGE)) {

                    }
                    requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
                }
                else {
                    new Torrent(getApplicationContext(), itemMmap.get("nomComplet"), itemMmap.get("ID"), itemMmap.get("taille"), itemMmap.get("uploader"), itemMmap.get("cat")).download();
                }
                return true;
            case R.id.torrent_context_menu_download_later:
                Toast.makeText(getApplicationContext(), "Envoi en cours...", Toast.LENGTH_LONG).show();
                new Torrent(getApplicationContext(), itemMmap.get("nomComplet"), itemMmap.get("ID")).bookmark();
                return true;
            case R.id.torrent_context_menu_download_later_not:
                Toast.makeText(getApplicationContext(), "Demande en cours...", Toast.LENGTH_LONG).show();
                new Torrent(getApplicationContext(), itemMmap.get("nomComplet"), itemMmap.get("ID")).unbookmark();
                return true;
            case R.id.torrent_context_menu_share:
                new Torrent(getApplicationContext(), itemMmap.get("nomComplet"), itemMmap.get("ID")).share();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void switchList(String catCode) {

        mSchedule = mTorrentsAdapter;
        ArrayList<HashMap<String, String>> filterListItem = new ArrayList<HashMap<String, String>>();

        for (int i = 0; i < mSchedule.getCount(); i++) {
            HashMap<String, String> nmap = (HashMap<String, String>) mSchedule.getItem(i);
            if (nmap.get("cat").contains(catCode))
                filterListItem.add(nmap);
        }

        SimpleAdapter mFilterAdapter = new SimpleAdapter(
                torrentsActivity.this.getBaseContext(), filterListItem,
                R.layout.item_torrent, new String[]{"nomComplet",
                "age", "taille", "avis", "seeders", "leechers", "uploader", "ratio", "completed", "ratioBase", "icon"}, new int[]{R.id.tNom
                , R.id.tAge, R.id.tTaille, R.id.tComments, R.id.tSeeders, R.id.tLeechers, R.id.tUploader, R.id.tRatio, R.id.tCompleted, R.id.tRatioBase, R.id.tIcon});

        maListViewPerso.setAdapter(mFilterAdapter);


        //Toast.makeText(getApplicationContext(), "Load category "+catCode+" ("+lv.getCount()+")", Toast.LENGTH_SHORT).show();

    }

    private void update() {
        try {
            //dialog = show(torrentsActivity.this, "t411.ch", this.getString(R.string.pleasewait), true, true);
            dialog = new ProgressDialog(this, R.style.AdTitleDialog);
            dialog.setMessage(this.getString(R.string.pleasewait));
            AdView mAdView;
            AdRequest adRequest;
            LayoutInflater inflater = getLayoutInflater();
            View view = inflater.inflate(R.layout.adtitlebar, null);
            mAdView = (AdView) view.findViewById(R.id.adView);
            adRequest = new AdRequest.Builder().addTestDevice(AdRequest.DEVICE_ID_EMULATOR).addTestDevice(Private.REAL_DEVICE).build();
            dialog.setCustomTitle(view);
            mAdView.loadAd(adRequest);
            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
        mF = new torrentFetcher();
        try {
            mF.execute();
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Erreur inattendue", Toast.LENGTH_SHORT).show();
            finish();
        }
    }


    private class AsyncRemoveFavorite extends AsyncTask<Void, String[], Void> {

        String msg;

        Connection.Response res;
        Document doc;
        Map<String, String> Cookies;

        @Override
        protected Void doInBackground(Void... arg0) {
            String username = prefs.getString("login", ""), password = prefs
                    .getString("password", "");

            try {

                doc = Jsoup.parse(new SuperT411HttpBrowser(getApplicationContext())
                        .login(username, password)
                        .skipLogin()
                        .connect(Default.URL_UNFAVORITE)
                        .addData("submit", "Supprimer")
                        .addData("ids[]", id)
                        .executeInAsyncTask());

            } catch (Exception e) {

            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
        }

    }

    private class addToFavorites extends AsyncTask<Void, String[], Void> {

        Connection.Response res = null;
        Document doc = null;
        String msg = null;
        Map<String, String> mCookies;
        String username, password;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            username = prefs.getString("login", "");
            password = prefs.getString("password", "");
        }

        @Override
        protected Void doInBackground(Void... voids) {

            try {
                doc = Jsoup.parse(new SuperT411HttpBrowser(getApplicationContext())
                        .login(username, password)
                        .connect(Default.URL_SEARCH_SAVE + (connectUrl.substring(connectUrl.lastIndexOf("=")) + "&order=" + order + "&type=" + type))
                        .addData("submit", "Valider")
                        .addData("name", name)
                        .executeInAsyncTask());


                msg = doc.select(".content ").first().text();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            Toast.makeText(getApplicationContext(), "COOKIE :" + mCookies.toString(), Toast.LENGTH_LONG).show();
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();

        }
    }

    private class torrentFetcher extends AsyncTask<Void, String, Void> {

        Connection.Response res = null;
        Document doc = null;
        int count = 0;

        @Override
        protected void onPreExecute() {
            dialog.setMessage(getString(R.string.connecting));
            super.onPreExecute();
        }

        @Override
        protected void onCancelled() {
            this.cancel(true);
            super.onCancelled();
        }

        @Override
        protected Void doInBackground(Void... arg0) {
            listItem.clear();

            try {

                pageListItem.clear();

                String url = connectUrl;

                url += "&order=" + order + "&type=" + type + "&page=" + paginator;

                new T411Logger(getApplicationContext()).writeLine("Envoi de la requête : " + url);

                doc = Jsoup.parse(new SuperT411HttpBrowser(getApplicationContext())
                        .login(prefs.getString("login", ""), prefs.getString("password", ""))
                        .connect(url)
                        .executeInAsyncTask());


            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                map = new HashMap<String, String>();
                map.put("icon", String.valueOf(R.drawable.ic_new_t411));
                map.put("name", "-- Tout --");
                map.put("code", "");
                catListItem.add(map);

                publishProgress(getString(R.string.fetchingCat));
                count = 0;

                for (Element hCat : doc.select("h3")) {
                    try {
                        map = new HashMap<String, String>();
                        String[] t_CatCode = hCat.nextElementSibling().select("tbody td a").first().attr("href").split("=");
                        map.put("icon", String.valueOf(new CategoryIcon(t_CatCode[t_CatCode.length - 1]).getIcon()));
                        map.put("name", hCat.text());
                        map.put("code", t_CatCode[t_CatCode.length-1]);
                        catListItem.add(map);
                        publishProgress(++count + " " + getString(R.string.catFetched));
                    } catch(Exception ex) {
                        ex.printStackTrace();
                    }
                }
                new T411Logger(getApplicationContext()).writeLine(count + " catégories trouvées");

                publishProgress(getString(R.string.fetchingPages));
                count = 0;
                for (Element page : doc.select(".pagebar").select("a")) {
                    if (!page.hasAttr("rel")) {
                        map = new HashMap<String, String>();
                        map.put("icon", String.valueOf(R.drawable.file));
                        map.put("name", page.text());
                        map.put("code", page.attr("href").substring(page.attr("href").lastIndexOf("=") + 1));
                        pageListItem.add(map);
                    }
                    publishProgress(++count + " " + getString(R.string.pagesFetched));
                }
                new T411Logger(getApplicationContext()).writeLine(count + " pages trouvées");
            } catch (Exception e) {
                e.printStackTrace();
            }

            publishProgress(getString(R.string.connecting));



            int base = connectUrl.equals(Default.URL_BOOKMARKS) ? 1 : 0;

            count = 0;

            try {
                for (Element table : doc.select("table.results tbody")) {
                    for (Element row : table.select("tr")) {
                        Elements tds = row.select("td");


                        String[] t_catCode = tds.get(base + 0).select("a").first().attr("href").split("=");
                        String catCode = t_catCode[t_catCode.length - 1];

                        try {

                            //récupération de l'ID
                            String[] t_ID = tds.get(base + 2).select("a").attr("href").split("=");

                            Long tTime = Long.parseLong(tds.get(base + 4).text().split(" ")[0]);
                            String temp = tds.get(base + 4).text().split(" ")[1];

                            switch (temp){
                                case "minutes":tTime = 0+tTime ;break;
                                case "minute":tTime = 0+tTime ;break;
                                case "heures":tTime = tTime*60;break;
                                case "heure":tTime = tTime*60;break;
                                case "jours":tTime = tTime * 1440;break;
                                case "jour":tTime = tTime * 1440;break;
                                case "semaines":tTime = tTime * 10080;break;
                                case "semaine":tTime = tTime * 10080;break;
                                case "mois":tTime = tTime * 43646;break;
                                case "moi":tTime = tTime * 43646;break;
                                case "ans":tTime = tTime * 523756;break;
                                case "an":tTime = tTime * 523756;break;
                            }

                            publishProgress(++count + " " + getString(R.string.torrents_found));
                            map = new HashMap<String, String>();
                            //map.put("nomComplet", tds.get(base + 1).select("a").first().attr("title").toString());
                            map.put("nomComplet", tds.get(base + 1).select("a").first().text().toString());
                            map.put("ID", t_ID[t_ID.length-1]);
                            map.put("age", tds.get(base + 4).text());
                            map.put("added", tTime.toString());
                            map.put("taille", new BSize(tds.get(base + 5).text()).convert());
                            map.put("avis", tds.get(base + 3).text());
                            map.put("seeders", tds.get(base + 7).text());
                            map.put("leechers", tds.get(base + 8).text());
                            map.put("uploader", tds.get(base + 1).select("dd > a.profile").text());
                            map.put("completed", tds.get(base + 6).text());
                            map.put("cat", catCode);

                            String tSize = tds.get(base + 5).text();
                            double estimatedDl = new BSize(prefs.getString("lastDownload", "? 0.00 GB")).getInMB() + new BSize(tSize).getInMB();
                            String estimatedRatio = String.format("%.2f", (new BSize(prefs.getString("lastUpload", "? 0.00 GB")).getInMB() / estimatedDl) - 0.01);

                            map.put("ratio", estimatedRatio);
                            map.put("icon", String.valueOf(new CategoryIcon(catCode).getIcon()));
                            map.put("ratioBase", String.format("%.2f", Float.valueOf(prefs.getString("lastRatio", "0"))));
                            listItem.add(map);

                        } catch (Exception e) {
                            e.printStackTrace();
                            new T411Logger(getApplicationContext()).writeLine("Erreur interne : " + e.getMessage());
                        }
                    }
                }
                if (prefs.getBoolean("sortByDate", false)) {
                    Comparator<HashMap<String, String>> comparator = new Comparator<HashMap<String, String>>() {
                        @Override
                        public int compare(HashMap<String, String> object1, HashMap<String, String> object2)
                        {
                            Integer test1 = Integer.parseInt(object1.get("added"));
                            Integer test2 = Integer.parseInt(object2.get("added"));
                            int test = test1.compareTo(test2);
                            return test;
                        }
                    };
                    Collections.sort(listItem, comparator);
                }
            } catch (RuntimeException rte) {

                /*new Handler().post(new Runnable() {
                    public void run() {
                        Toast.makeText(getApplicationContext(), getApplicationContext().getString(R.string.runtime_ex), Toast.LENGTH_LONG).show();
                    }
                });*/

            } catch (Exception e) {
                e.printStackTrace();
            }
            new T411Logger(getApplicationContext()).writeLine(count + " torrents sur cette page");

            return null;
        }

        @Override
        protected void onProgressUpdate(String... value) {
            try {
                dialog.setMessage(value[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }

            super.onProgressUpdate();
        }

        @Override
        protected void onPostExecute(Void result) {

            try {
                Element elmtPrev = doc.select(".pagebar a").first();
                strPrev = null;
                if (elmtPrev.hasAttr("rel")) {
                    strPrev = elmtPrev.attr("href").substring(elmtPrev.attr("href").lastIndexOf("=") + 1);
                    prev = (ImageButton) findViewById(R.id.navbtn_prev);
                    prev.setVisibility(View.VISIBLE);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                Element elmtNext = doc.select(".pagebar a").last();
                strNext = null;
                if (elmtNext.hasAttr("rel")) {
                    strNext = elmtNext.attr("href").substring(elmtNext.attr("href").lastIndexOf("=") + 1);
                    next = (ImageButton) findViewById(R.id.navbtn_next);
                    next.setVisibility(View.VISIBLE);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                mSchedule = new SimpleAdapter(
                        getBaseContext(), catListItem,
                        R.layout.item_searchoptions, new String[]{"icon", "name",
                        "code"}, new int[]{R.id.lso_icon,
                        R.id.lso_title, R.id.lso_code});

                catList.setAdapter(mSchedule);

                pageName = doc.select(".pagebar").select("span").first().text();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                mSchedule = new SimpleAdapter(
                        getBaseContext(), pageListItem,
                        R.layout.item_searchoptions, new String[]{"icon", "name",
                        "code"}, new int[]{R.id.lso_icon,
                        R.id.lso_title, R.id.lso_code});

                PagesList.setAdapter(mSchedule);
                navbar.setVisibility(PagesList.getCount() > 0 ? View.VISIBLE : View.GONE);

                prev = (ImageButton) findViewById(R.id.navbtn_prev);
                prev.setVisibility(strPrev == null ? View.INVISIBLE : View.VISIBLE);

                next = (ImageButton) findViewById(R.id.navbtn_next);
                next.setVisibility(strNext == null ? View.INVISIBLE : View.VISIBLE);

            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                TextView tv = (TextView) findViewById(R.id.navbar_pagesText);
                tv.setText(pageName);
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {

                mTorrentsAdapter = new SimpleAdapter(
                        torrentsActivity.this.getBaseContext(), listItem,
                        R.layout.item_torrent, new String[]{"nomComplet",
                        "age", "taille", "avis", "seeders", "leechers", "uploader", "ratio", "completed", "ratioBase", "icon"}, new int[]{R.id.tNom
                        , R.id.tAge, R.id.tTaille, R.id.tComments, R.id.tSeeders, R.id.tLeechers, R.id.tUploader, R.id.tRatio, R.id.tCompleted, R.id.tRatioBase, R.id.tIcon});

                maListViewPerso.setAdapter(mTorrentsAdapter);

                if (doc == null) {
                    Toast.makeText(getApplicationContext(), getApplicationContext().getString(R.string.no_data_from_server), Toast.LENGTH_LONG).show();
                    new T411Logger(getApplicationContext()).writeLine("Aucune donnée reçue");
                }

                Handler handler = new Handler();
                //if (maListViewPerso.getCount() < 1) {
                if (count < 1) {
                    Toast.makeText(getApplicationContext(), getApplicationContext().getString(R.string.no_result), Toast.LENGTH_LONG).show();
                    new T411Logger(getApplicationContext()).writeLine("Aucune entrée dans la liste, fermeture.");
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    }, 1000);
                }
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        dialog.cancel();
                    }
                }, 500);
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), getApplicationContext().getString(R.string.no_data_from_server), Toast.LENGTH_LONG).show();
                new T411Logger(getApplicationContext()).writeLine("Aucune donnée reçue");
                finish();
            }
        }
    }
}
