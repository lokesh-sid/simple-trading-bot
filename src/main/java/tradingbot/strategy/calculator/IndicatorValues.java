package tradingbot.strategy.calculator;

import java.io.Serializable;

public class IndicatorValues implements Serializable {
    private double rsi;
    private double macd;
    private double signal;
    private double lowerBand;
    private double upperBand;

    public double getRsi() { return rsi; }
    public void setRsi(double rsi) { this.rsi = rsi; }
    public double getMacd() { return macd; }
    public void setMacd(double macd) { this.macd = macd; }
    public double getSignal() { return signal; }
    public void setSignal(double signal) { this.signal = signal; }
    public double getLowerBand() { return lowerBand; }
    public void setLowerBand(double lowerBand) { this.lowerBand = lowerBand; }
    public double getUpperBand() { return upperBand; }
    public void setUpperBand(double upperBand) { this.upperBand = upperBand; }
}