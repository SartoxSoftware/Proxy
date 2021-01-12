package open.nano.proxy;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Parallel
{
    public static ExecutorService threadPool;
    public static int threads = 50;

    public static <T> void For(final T[] elements, final Operation<T> operation)
    {
        try
        {
            threadPool = Executors.newFixedThreadPool(threads);
            threadPool.invokeAll(createCallables(elements, operation));
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    public static <T> Collection<Callable<Void>> createCallables(final T[] elements, final Operation<T> operation)
    {
        List<Callable<Void>> callables = new LinkedList<>();
        for (int i = 0; i < elements.length; i++)
        {
            T elem = elements[i];
            callables.add(() ->
            {
                operation.perform(elem);
                return null;
            });
        }

        return callables;
    }

    public interface Operation<T>
    {
        void perform(T pParameter) throws IOException, InterruptedException;
    }
}
