/*******************************************************************************
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2018 Ta4j Organization 
 *   & respective authors (see AUTHORS)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package org.ta4j.core.analysis;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The return rates.
 * </p>
 * This class allows to compute the return rate of a price time-series
 */
public class Returns implements Indicator<Num> {

    public enum ReturnType {
        LOG {
            @Override
            public Num calculate(Num xNew, Num xOld) {
                // r_i = ln(P_i/P_(i-1))
                return (xNew.dividedBy(xOld)).log();
            }
        },
        ARITHMETIC {
            @Override
            public Num calculate(Num xNew, Num xOld) {
                // r_i = P_i/P_(i-1) - 1
                return xNew.dividedBy(xOld).minus(one);
            }
        };

        /**
         * @return calculate a single return rate
         */
        public abstract Num calculate(Num xNew, Num xOld);
    }

    private final ReturnType type;

    /** The time series */
    private final TimeSeries timeSeries;

    /** The return rates */
    private List<Num> values;

    /** Unit element for efficient arithmetic return computation */
    private static Num one;
    private static Num minusOne;


    /**
     * Constructor.
     * @param timeSeries the time series
     * @param trade a single trade
     */
    public Returns(TimeSeries timeSeries, Trade trade, ReturnType type) {
        one = timeSeries.numOf(1);
        minusOne = timeSeries.numOf(-1);
        this.timeSeries = timeSeries;
        this.type = type;
        // at index 0, there is no return
        values = new ArrayList<>(Collections.singletonList(NaN.NaN));
        calculate(trade);

        fillToTheEnd();
    }

    /**any
     * Constructor.
     * @param timeSeries the time series
     * @param tradingRecord the trading record
     * @param type type of the return
     */
    public Returns(TimeSeries timeSeries, TradingRecord tradingRecord, ReturnType type) {
        this(timeSeries, tradingRecord, type, timeSeries.getEndIndex());
    }

    /**
     * Constructor.
     * @param timeSeries the time series
     * @param tradingRecord the trading record
     * @param type type of the return
     * @param finalIndex index up until returns of open trades are considered
     */
    public Returns(TimeSeries timeSeries, TradingRecord tradingRecord, ReturnType type, int finalIndex) {
        one = timeSeries.numOf(1);
        minusOne = timeSeries.numOf(-1);
        this.timeSeries = timeSeries;
        this.type = type;
        // at index 0, there is no return
        values = new ArrayList<>(Collections.singletonList(NaN.NaN));
        calculate(tradingRecord, finalIndex);

        fillToTheEnd();
    }

    public List<Num> getValues() { return values; }

    /**
     * @param index the bar index
     * @return the return rate value at the index-th position
     */
    @Override
    public Num getValue(int index) {
        return values.get(index);
    }

    @Override
    public TimeSeries getTimeSeries() {
        return timeSeries;
    }

    @Override
    public Num numOf(Number number) {
        return timeSeries.numOf(number);
    }

    /**
     * @return the size of the return series.
     */
    public int getSize() {
        return timeSeries.getBarCount() - 1;
    }

    public void calculate(Trade trade) {
        calculate(trade, timeSeries.getEndIndex());
    }

    /**
     * Calculates the cash flow for a single trade (including accrued cashflow for open trades).
     * @param trade a single trade
     * @param finalIndex index up until cash flow of open trades is considered
     */
    public void calculate(Trade trade, int finalIndex) {
        boolean isLongTrade = trade.getEntry().isBuy();
        int endIndex = CashFlow.determineEndIndex(trade, finalIndex, timeSeries.getEndIndex());
        final int entryIndex = trade.getEntry().getIndex();
        int begin = entryIndex + 1;
        if (begin > values.size()) {
            values.addAll(Collections.nCopies(begin - values.size(), timeSeries.numOf(0)));
        }

        int startingIndex = Math.max(begin, 1);
        int nPeriods = endIndex - entryIndex;
        Num holdingCost = trade.getHoldingCost(endIndex);
        Num avgCost = holdingCost.dividedBy(holdingCost.numOf(nPeriods));

        // returns are per period (iterative). Base price needs to be updated accordingly
        Num lastPrice = trade.getEntry().getNetPrice();
        Num previousReturnFactor = one;
        for (int i = startingIndex; i < endIndex; i++) {
            Num intermediateNetPrice = CashFlow.addCost(timeSeries.getBar(i).getClosePrice(), avgCost, isLongTrade);

            Num strategyReturn;
            if (trade.getEntry().isBuy()) {
                strategyReturn = type.calculate(intermediateNetPrice, lastPrice);
            } else {
                // include leverage ratio
                Num tradeReturnFactor = updateReturnFactor(trade.getEntry().getNetPrice(), intermediateNetPrice);
                strategyReturn = calculateLeveragedReturn(intermediateNetPrice, lastPrice, tradeReturnFactor,
                        previousReturnFactor);

                // update return factor
                previousReturnFactor = tradeReturnFactor;
            }
            values.add(strategyReturn);
            // update base price
            lastPrice = timeSeries.getBar(i).getClosePrice();
        }

        // add net return at exit trade
        Num exitPrice;
        if (trade.getExit() != null) {
            exitPrice = trade.getExit().getNetPrice();
        }
        else {
            exitPrice = timeSeries.getBar(endIndex).getClosePrice();
        }

        Num strategyReturn;
        Num netExitPrice = CashFlow.addCost(exitPrice, avgCost, isLongTrade);
        if (trade.getEntry().isBuy()) {
            strategyReturn = type.calculate(netExitPrice, lastPrice);
        } else {
            // include leverage ratio
            Num tradeReturnFactor = updateReturnFactor(trade.getEntry().getNetPrice(), netExitPrice);
            strategyReturn = calculateLeveragedReturn(netExitPrice, lastPrice, tradeReturnFactor, previousReturnFactor);
        }
        values.add(strategyReturn);
    }

    /**
     * Calculates the current return factor since the trade entry.
     * @param entryPrice Price of trade entry
     * @param currentPrice Current price of the asset
     * @return current return factor
     */
    private Num updateReturnFactor(Num entryPrice, Num currentPrice) {
        // Return factor needed for the leverage ratio computation
        Num currentTradeReturnFactor = null;
        switch (type) {
            case LOG:
                // Logarithmic returns are additive. No adjustment necessary
                break;
            case ARITHMETIC:
                currentTradeReturnFactor = one.minus(type.calculate(currentPrice, entryPrice));
                break;
            default:
                throw new IllegalArgumentException();
        }
        return currentTradeReturnFactor;
    }

    /**
     * Calculates the return including leverage effects (at time t)
     * @param currentPrice price at time t
     * @param lastPrice price at time t-1
     * @param currentReturnFactor return factor at time t
     * @param previousReturnFactor  return factor at time t-1
     * @return leveraged return
     */
    private Num calculateLeveragedReturn(Num currentPrice, Num lastPrice, Num currentReturnFactor, Num previousReturnFactor) {
        Num leveragedReturn;
        switch (type) {
            case LOG:
                // log returns are additive, leverage ratio is constant
                leveragedReturn = type.calculate(currentPrice, lastPrice).multipliedBy(minusOne);
                break;
            case ARITHMETIC:
                leveragedReturn = type.calculate(currentReturnFactor, previousReturnFactor);
                break;
            default:
                throw new IllegalArgumentException();
        }
        return leveragedReturn;
    }

    /**
     * Calculates the returns for a trading record.
     * @param tradingRecord the trading record
     * @param finalIndex index up until returns of open trades are considered
     */
    private void calculate(TradingRecord tradingRecord, int finalIndex) {
        // For each trade...
        tradingRecord.getTrades().forEach(this::calculate);

        // Add accrued cash flow of open trade
        if (tradingRecord.getCurrentTrade().isOpened()) {
            calculate(tradingRecord.getCurrentTrade(), finalIndex);
        }
    }

    /**
     * Fills with zeroes until the end of the series.
     */
    private void fillToTheEnd() {
        if (timeSeries.getEndIndex() >= values.size()) {
            values.addAll(Collections.nCopies(timeSeries.getEndIndex() - values.size() + 1, timeSeries.numOf(0)));
        }
    }
}