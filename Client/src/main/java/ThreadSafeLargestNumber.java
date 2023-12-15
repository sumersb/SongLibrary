import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;

public class ThreadSafeLargestNumber {
    private AtomicInteger largestNumber;

    public ThreadSafeLargestNumber(int number) {
        largestNumber = new AtomicInteger(number);
    }

    public int update(int number) {
        return largestNumber.updateAndGet(current -> Math.max(current, number));
    }

    public int getLargest() {
        return largestNumber.get();
    }

    public String getRandomInt() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(1, largestNumber.get()+1));
    }
}