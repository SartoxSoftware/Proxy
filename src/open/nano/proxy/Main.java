package open.nano.proxy;

import open.java.toolkit.Regex;
import open.java.toolkit.console.Console;
import open.java.toolkit.console.LogType;
import open.java.toolkit.console.ansi.Foreground;
import open.java.toolkit.files.FileParser;
import open.java.toolkit.files.Files;
import open.java.toolkit.http.Request;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main
{
    public static void main(String[] args) throws IOException, InterruptedException
    {
        Console.setTitle("Nano Proxy");
        Request.setVersion(HttpClient.Version.HTTP_1_1);

        System.out.println("Scrape proxies from sources? (true/false)");
        boolean scraping = Boolean.parseBoolean(Console.readLine());

        if (scraping)
        {
            if (!Files.fileExists("proxies.txt"))
                Files.writeFile("proxies.txt", "", false);

            Parallel.For(Files.readFile("sources.txt").split("\n"), source ->
            {
                Console.ansiWriteLine(Foreground.YELLOW, "Scraping from source : " + source, LogType.INFO);

                String body = Request.sendGet(source).body();
                ArrayList<String> matches = Regex.getMatches(body, "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})(?=[^\\d])\\s*:?\\s*(\\d{2,5})");

                Files.writeFile("proxies.txt", matches.toString()
                        .replace(", ", "\n")
                        .replace("[", "")
                        .replace("]", "") + "\n", true);

                Console.ansiWriteLine(Foreground.YELLOW, "Scraped " + matches.size() + " proxies from source : " + source, LogType.INFO);
            });
        }

        if (!Files.fileExists("live.txt"))
            Files.writeFile("live.txt", "", false);

        System.out.println("Amount of threads :");
        int threads = Integer.parseInt(System.console().readLine());
        Parallel.threads = threads;

        Console.setTitle("Nano Proxy | Loading...");

        String[] proxies = Files.readFile("proxies.txt").split("\n");
        String[] judges = Files.readFile("judges.txt").split("\n");
        Random random = ThreadLocalRandom.current();
        FileParser parser = new FileParser("config.txt", "#", ":", true, 1);
        AtomicInteger checked = new AtomicInteger(), live = new AtomicInteger();

        boolean fastChecking = parser.parseBoolean("fastChecking");
        int timeout = parser.parseInt("timeout");

        Request.setTimeout(timeout);

        Parallel.For(proxies, proxy ->
        {
            try
            {
                Console.setTitle("Nano Proxy | Checking (" + checked + "/" + proxies.length + ") | " + live + " Live");

                String[] array = proxy.split(":");
                boolean valid = false;

                if (fastChecking)
                    valid = InetAddress.getByName(array[0]).isReachable(timeout);
                else
                {
                    Request.setProxy(ProxySelector.of(new InetSocketAddress(array[0], Integer.parseInt(array[1]))));

                    String judge = judges[random.nextInt(judges.length)];
                    HttpResponse<String> response = Request.sendGet(judge);

                    valid = response.statusCode() == 200;
                }

                if (valid)
                {
                    Console.ansiWrite(Foreground.GREEN, "[GOOD]    " + proxy, LogType.INFO);
                    Files.writeFile("live.txt", proxy + "\n", true);

                    live.getAndIncrement();
                } else Console.ansiWrite(Foreground.RED, "[BAD]     " + proxy, LogType.INFO);
            }
            catch (Throwable t)
            {
                Console.ansiWrite(Foreground.BLUE, "[UNKNOWN] " + proxy, LogType.INFO);
            }

            checked.getAndIncrement();
        });

        System.out.println("Done!");

        System.out.println("Press Enter key to exit program...");
        System.in.read();
    }
}
