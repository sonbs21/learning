
public class bai1 {
    public static void main(String[] args) {
        Bank b = new Bank("Pionero");

        System.err.println("1:" + b.deposit(10));

        try {
            b.deposit(-2);
        } catch (Exception e) {
            System.err.println(e);
        }

        System.err.println("3:" + b.withdraw(3));
        try {
            System.err.println("4:" + b.withdraw(-3));
        } catch (Exception e) {
            System.err.println(e);
        }
        try {
            System.err.println("5:" + b.withdraw(20));
        } catch (Exception e) {
            System.err.println(e);
        }
        System.err.println("6:" + b.getBalance());

    }
}

class Bank {
    private double balance;
    private final String owner;

    Bank(String owner) {
        this.owner = owner;
    }

    public double getBalance() {
        return balance;
    }

    public double deposit(double amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Amount must be positive");

        this.balance += amount;
        return this.balance;
    }

    public double withdraw(double amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Amount must be positive");
        else if (amount > balance)
            throw new IllegalStateException(owner + " don't enough money");

        return this.balance -= amount;
    }

}
