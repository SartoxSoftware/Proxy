package open.nano.proxy;

import open.java.toolkit.Arrays;
import open.java.toolkit.Regex;
import open.java.toolkit.System;
import open.java.toolkit.console.Console;
import open.java.toolkit.console.LogType;
import open.java.toolkit.console.ansi.Foreground;
import open.java.toolkit.files.FileParser;
import open.java.toolkit.files.Files;
import open.java.toolkit.http.Request;
import open.java.toolkit.threading.Parallel;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.*;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main
{
    public static void main(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException
    {
        Console.setTitle("Nano Proxy");
        Request.setVersion(HttpClient.Version.HTTP_1_1);

        Console.writeLine("Scrape proxies from sources? (true/false)", LogType.INFO);
        boolean scraping = Boolean.parseBoolean(Console.readLine());

        if (scraping)
        {
            if (!Files.fileExists("proxies.txt"))
                Files.createFile("proxies.txt");

            Parallel.forEach(Files.readFile("sources.txt").split("\n"), source ->
            {
                Console.ansiWriteLine(Foreground.YELLOW, "Scraping from source : " + source, LogType.INFO);

                String body = Request.sendGet(source).body();
                ArrayList<String> matches = Regex.getMatches(body, "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})(?=[^\\d])\\s*:?\\s*(\\d{2,5})");

                Files.writeLines("proxies.txt", Arrays.toStringArray(matches.toArray()), true);
                Console.ansiWriteLine(Foreground.YELLOW, "Scraped " + matches.size() + " proxies from source : " + source, LogType.INFO);
            });
        }

        if (!Files.fileExists("live.txt"))
            Files.createFile("live.txt");

        Console.writeLine("Amount of threads :", LogType.INFO);
        Parallel.threads = Integer.parseInt(Console.readLine());

        Console.setTitle("Nano Proxy | Loading...");

        String[] proxies = Files.readLines("proxies.txt");
        ArrayList<String> list = Arrays.toArrayList(proxies);
        Collections.shuffle(list);
        proxies = list.toArray(new String[list.size()]);

        String[] judges = Files.readLines("judges.txt");
        SecureRandom random = SecureRandom.getInstanceStrong();
        FileParser parser = new FileParser("config.txt", "#", ":", true, 1);
        AtomicInteger checked = new AtomicInteger(), live = new AtomicInteger();

        boolean fastChecking = parser.parseBoolean("fastChecking");
        int timeout = parser.parseInt("timeout");
        boolean debugging = parser.parseBoolean("debugging");
        boolean lessAccuracy = parser.parseBoolean("lessAccuracy");

        Request.setTimeout(timeout);

        String[] finalProxies = proxies;
        Parallel.forEach(proxies, proxy ->
        {
            String judge = null;

            try
            {
                Console.setTitle("Nano Proxy | Checking (" + checked + "/" + finalProxies.length + ") | " + live + " Live");

                String[] array = proxy.split(":");
                boolean valid;
                int statusCode = 0;

                if (fastChecking)
                    valid = InetAddress.getByName(array[0]).isReachable(timeout);
                else
                {
                    Request.setProxy(ProxySelector.of(new InetSocketAddress(array[0], Integer.parseInt(array[1]))));

                    judge = judges[random.nextInt(judges.length)];
                    HttpResponse<String> response = Request.sendGet(judge);

                    statusCode = response.statusCode();
                    valid = statusCode == 200;
                }

                if (valid)
                {
                    Console.ansiWriteLine(Foreground.GREEN, "[GOOD]    " + proxy, LogType.INFO);
                    Files.writeString("live.txt", proxy + System.newLine, true);

                    live.getAndIncrement();
                }
                else
                {
                    if (lessAccuracy && (statusCode == 302 || statusCode == 501 || statusCode == 503))
                    {
                        Console.ansiWriteLine(Foreground.GREEN, "[GOOD]    " + proxy, LogType.INFO);
                        Files.writeString("live.txt", proxy + System.newLine, true);

                        live.getAndIncrement();
                    } else Console.ansiWriteLine(Foreground.RED, "[BAD]     " + proxy + (debugging && !fastChecking ? " - Status code : " + statusCode : ""), LogType.INFO);
                }
            }
            catch (Throwable t)
            {
                Console.ansiWriteLine(Foreground.BLUE, "[UNKNOWN] " + (debugging && !fastChecking ? "(" + judge + ") " : "") + proxy + (debugging ? " - " + t.getMessage() : ""), LogType.INFO);
            }

            checked.getAndIncrement();
        });

        Console.writeLine("Done!", LogType.INFO);

        Console.writeLine("Press Enter key to exit program...", LogType.INFO);
        Console.readLine();

        java.lang.System.exit(0);
    }
}
