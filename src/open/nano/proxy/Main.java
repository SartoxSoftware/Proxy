package open.nano.proxy;

import open.java.toolkit.Regex;
import open.java.toolkit.System;
import open.java.toolkit.console.Console;
import open.java.toolkit.console.LogType;
import open.java.toolkit.console.ansi.Foreground;
import open.java.toolkit.files.FileParser;
import open.java.toolkit.files.Files;
import open.java.toolkit.http.Request;
import open.java.toolkit.threading.Parallel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Main
{
    public static void main(String[] args) throws NoSuchAlgorithmException
    {
        Console.setTitle("Nano Proxy v1.1 | JavaToolkit v" + System.getToolkitVersion());
        FileParser parser = new FileParser("config.txt", "#", ":", true, 1);

        int timeout = parser.getInt("timeout");
        boolean debugging = parser.getBoolean("debugging");
        boolean lessAccuracy = parser.getBoolean("lessAccuracy");
        boolean showBad = parser.getBoolean("showBad");
        boolean showUnknown = parser.getBoolean("showUnknown");
        boolean scrapeLinks = parser.getBoolean("scrapeLinks");
        boolean scrapeProxies = parser.getBoolean("scrapeProxies");
        boolean capture = parser.getBoolean("capture");

        System.showErrors = debugging;
        System.logErrors = debugging;

        Request.setTimeout(timeout);
        Request.getBuilder().followRedirects(HttpClient.Redirect.ALWAYS);
        Request.getBuilder().version(HttpClient.Version.HTTP_1_1);

        if (scrapeLinks)
        {
            Console.setTitle("Nano Proxy | Scraping links...");
            String[] keywords = Files.readLines("keywords.txt");
            AtomicInteger words = new AtomicInteger();

            Parallel.forEach(keywords, keyword ->
            {
                Console.setTitle("Nano Proxy | Scraping links | " + words + " / " + keywords.length + " Keywords");

                String response = (String) Request.send("https://www.bing.com/search?q=" + keyword.replace(" ", "+"), "get", null).body();
                ArrayList<String> matches = Regex.getMatches(response, "((http|https)://)(www.)?[a-zA-Z0-9@:%._\\+~#?&//=]{2,256}\\.[a-z]{2,6}\\b([-a-zA-Z0-9@:%._\\+~#?&//=]*)");

                int size = matches.size();
                if (size > 0)
                {
                    Console.ansiWriteLine(Foreground.GREEN, "Found " + size + " links. (" + keyword + ")", LogType.INFO);
                    Files.writeLines("sources.txt", matches.stream().filter(link -> !link.contains("aclick?ld=")).toArray(String[]::new), true);
                }

                words.getAndIncrement();
            });
        }

        if (scrapeProxies)
        {
            Console.setTitle("Nano Proxy | Scraping proxies...");

            Parallel.forEach(Files.readLines("sources.txt"), source ->
            {
                Console.ansiWriteLine(Foreground.YELLOW, "Scraping from source : " + source, LogType.INFO);

                String body = (String) Request.send(source, "get", null).body();
                ArrayList<String> matches = Regex.getMatches(body, "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})(?=[^\\d])\\s*:?\\s*(\\d{2,5})");

                Files.writeLines("proxies.txt", matches.toArray(String[]::new), true);
                Console.ansiWriteLine(Foreground.YELLOW, "Scraped " + matches.size() + " proxies from source : " + source, LogType.INFO);
            });
        }

        Console.writeLine("Amount of threads :", LogType.INFO);
        Parallel.threads = Integer.parseInt(Console.readLine());

        Console.setTitle("Nano Proxy | Loading...");

        String[] proxies = Files.readLines("proxies.txt");
        String[] judges = Files.readLines("judges.txt");

        SecureRandom random = SecureRandom.getInstanceStrong();
        AtomicInteger checked = new AtomicInteger(), live = new AtomicInteger();

        String[] l2Headers = new String[]
                {
                        "http_ACCPROXYWS",
                        "http_Cdn_Src_Ip",
                        "http_Client_IP",
                        "http_CUDA_CLIIP",
                        "http_Forwarded",
                        "http_Forwarded_For",
                        "http_REMOTE_HOST",
                        "http_X_Client_Ip",
                        "http_X_Coming_From",
                        "http_X_Forwarded",
                        "http_X_Forwarded_For",
                        "http_X_Forwarded_For_IP",
                        "http_X_Forwarded_Host",
                        "http_X_Forwarded_Server",
                        "http_X_Host",
                        "http_X_Network_Info",
                        "http_X_Nokia_RemoteSocket",
                        "http_X_ProxyUser_IP",
                        "http_X_QIHOO_IP",
                        "http_X_Real_IP",
                        "http_XCnool_forwarded_for",
                        "http_XCnool_remote_addr"
                };

        String[] l4Headers = new String[]
                {
                        "http_Mt_Proxy_ID",
                        "http_Proxy_agent",
                        "http_Proxy_Connection",
                        "http_Surrogate_Capability",
                        "http_Via",
                        "http_X_Accept_Encoding",
                        "http_X_ARR_LOG_ID",
                        "http_X_Authenticated_User",
                        "http_X_BlueCoat_Via",
                        "http_X_Cache",
                        "http_X_CID_HASH",
                        "http_X_Content_Opt",
                        "http_X_D_Forwarder",
                        "http_X_Fikker",
                        "http_X_Forwarded_Port",
                        "http_X_Forwarded_Proto",
                        "http_X_IMForwards",
                        "http_X_Loop_Control",
                        "http_X_MATO_PARAM",
                        "http_X_NAI_ID",
                        "http_X_Nokia_Gateway_Id",
                        "http_X_Nokia_LocalSocket",
                        "http_X_Original_URL",
                        "http_X_Proxy_ID",
                        "http_X_Roaming",
                        "http_x_teamsite_preremap",
                        "http_X_Tinyproxy",
                        "http_X_TurboPage",
                        "http_X_Varnish",
                        "http_X_Via",
                        "http_X_WAP_Profile",
                        "http_X_WrProxy_ID",
                        "http_http_X_XFF_0",
                        "http_Xroxy_Connection"
                };

        AtomicInteger cpm = new AtomicInteger();
        AtomicInteger clock = new AtomicInteger();

        Thread timer = new Thread(() ->
        {
            while (true)
            {
                clock.getAndIncrement();
                try
                {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }
        });

        Thread thread = new Thread(() ->
        {
            while (true)
            {
                long total = Runtime.getRuntime().totalMemory();
                long free = Runtime.getRuntime().freeMemory();
                long used = total - free;

                cpm.set(checked.get() * 60 / clock.get());

                Console.setTitle("Nano Proxy | Memory : " + (used / 1024 / 1024) + " / " + (total / 1024 / 1024) + " MB | Checking (" + checked + "/" + proxies.length + ") | " + live + " Live | " + cpm + " CPM");

                try
                {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }
        });

        timer.start();
        try
        {
            Thread.sleep(1100);
        } catch (InterruptedException ignored) {}
        thread.start();

        Parallel.forEach(proxies, proxy ->
        {
            Request.setProxy(proxy);

            String judge = judges[random.nextInt(judges.length)];
            try
            {
                long start = java.lang.System.nanoTime();
                boolean reachable = InetAddress.getByName(proxy.split(":")[0]).isReachable(timeout);
                long finish = java.lang.System.nanoTime();

                if (reachable)
                {
                    HttpResponse<String> response = (HttpResponse<String>) Request.send(judge, "get", null);
                    int status = response.statusCode();

                    if (!lessAccuracy ? status == 200 : (status == 200 || status == 302 || status == 501 || status == 503) && !System.errorOccurred)
                    {
                        String ping = TimeUnit.MILLISECONDS.convert(finish - start, TimeUnit.NANOSECONDS) + "ms";
                        String anonymity = "Unknown";

                        if (capture)
                        {
                            String azenv = (String) Request.send("http://azenv.net/", "get", null).body();

                            for (String l2 : l2Headers)
                                if (azenv.contains(l2.toUpperCase()))
                                {
                                    anonymity = "Transparent";
                                    break;
                                }

                            if (!anonymity.equals("Unknown"))
                                for (String l4 : l4Headers)
                                    if (azenv.contains(l4.toUpperCase()))
                                    {
                                        anonymity = "Anonymous";
                                        break;
                                    }

                            if (!anonymity.equals("Unknown")) anonymity = "Elite";
                        }

                        String str = proxy + (capture ? " | " + ping + " | " + anonymity : "");

                        Console.ansiWriteLine(Foreground.GREEN, "[GOOD]    " + str, LogType.INFO);
                        Files.writeString("live.txt", str + System.newLine, true);

                        live.getAndIncrement();
                    }
                    else
                    {
                        System.errorOccurred = false;

                        if (showBad)
                            Console.ansiWriteLine(Foreground.RED, "[BAD]     " + proxy + (debugging ? " - Status code : " + status + " (" + judge + ")" : ""), LogType.INFO);
                    }
                }
                else
                {
                    if (showBad)
                        Console.ansiWriteLine(Foreground.RED, "[BAD]     " + proxy + (debugging ? " - Reachable : " + reachable + " (" + judge + ")" : ""), LogType.INFO);
                }
            }
            catch (IOException ex)
            {
                if (showUnknown)
                    Console.ansiWriteLine(Foreground.BLUE, "[UNKNOWN] " + (debugging ? "(" + judge + ") " : "") + proxy + (debugging ? " - " + ex.getMessage() : ""), LogType.INFO);
            }

            checked.getAndIncrement();
        });

        Console.writeLine("Done!", LogType.INFO);

        Console.writeLine("Press Enter key to exit program...", LogType.INFO);
        Console.readLine();
    }
}
