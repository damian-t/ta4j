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

import org.ta4j.core.*;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The cash flow.
 * </p>
 * This class allows to follow the money cash flow involved by a list of trades over a time series.
 */
public class CashFlow implements Indicator<Num> {

    /** The time series */
    private final TimeSeries timeSeries;

    /** The cash flow values */
    private List<Num> values;

    /**
     * Constructor for cash flows of a closed trade.
     * @param timeSeries the time series
     * @param trade a single trade
     */
    public CashFlow(TimeSeries timeSeries, Trade trade) {
        this.timeSeries = timeSeries;
        values = new ArrayList<>(Collections.singletonList(numOf(1)));
        calculate(trade);
        fillToTheEnd();
    }

    /**
     * Constructor for cash flows of closed trades of a trading record.
     * @param timeSeries the time series
     * @param tradingRecord the trading record
     */
    public CashFlow(TimeSeries timeSeries, TradingRecord tradingRecord) {
        this.timeSeries = timeSeries;
        values = new ArrayList<>(Collections.singletonList(numOf(1)));
        calculate(tradingRecord);

        fillToTheEnd();
    }

    /**
     * Constructor.
     * @param timeSeries the time series
     * @param tradingRecord the trading record
     * @param finalIndex index up until cash flows of open trades are considered
     */
    public CashFlow(TimeSeries timeSeries, TradingRecord tradingRecord, int finalIndex) {
        this.timeSeries = timeSeries;
        values = new ArrayList<>(Collections.singletonList(numOf(1)));
        calculate(tradingRecord, finalIndex);

        fillToTheEnd();
    }

    /**
     * @param index the bar index
     * @return the cash flow value at the index-th position
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
     * @return the size of the time series
     */
    public int getSize() {
        return timeSeries.getBarCount();
    }

    /**
     * Calculates the cash flow for a single trade (closed).
     * @param trade a single trade
     */
    private void calculate(Trade trade) {
        if (trade.isOpened()) { throw new IllegalArgumentException("Trade is not closed"); }
        calculate(trade, trade.getExit().getIndex());
    }

    /**
     * Calculates the cash flow for a single trade (including accrued cashflow for open trades).
     * @param trade a single trade
     * @param finalIndex index up until cash flow of open trades is considered
     */
    private void calculate(Trade trade, int finalIndex) {
        int endIndex = determineEndIndex(trade, finalIndex);
        final int entryIndex = trade.getEntry().getIndex();
        int begin = entryIndex + 1;
        if (begin > values.size()) {
            Num lastValue = values.get(values.size() - 1);
            values.addAll(Collections.nCopies(begin - values.size(), lastValue));
        }
        int startingIndex = Math.max(begin, 1);
        int nPeriods = endIndex - entryIndex;
        Num totalCost = trade.calculateCost(endIndex, timeSeries.getBar(endIndex).getClosePrice());
        Num avgCost = totalCost.dividedBy(totalCost.numOf(nPeriods));
        for (int i = startingIndex; i <= endIndex; i++) {
            Num ratio;
            if (trade.getEntry().isBuy()) {
                ratio = getAdjustedRatio(entryIndex, i, avgCost);
            } else {
                // TODO: WRONG: should be -(ratio-1). Multiplicative setup for short trades flawed
                ratio = getRatio(i, entryIndex);
            }
            values.add(values.get(entryIndex).multipliedBy(ratio));
        }
    }

    private Num getAdjustedRatio(int entryIndex, int exitIndex, Num avgCost) {
        // TODO: avgCost is for the total amount
        return (timeSeries.getBar(exitIndex).getClosePrice().minus(avgCost)).dividedBy(timeSeries.getBar(entryIndex).getClosePrice());
    }


    private Num getRatio(int entryIndex, int exitIndex) {
        return timeSeries.getBar(exitIndex).getClosePrice().dividedBy(timeSeries.getBar(entryIndex).getClosePrice());
    }

    /**
     * Calculates the cash flow for the closed trades of a trading record.
     * @param tradingRecord the trading record
     */
    private void calculate(TradingRecord tradingRecord) {
        for (Trade trade : tradingRecord.getTrades()) {
            // For each trade...
            calculate(trade);
        }
    }

    /**
     * Calculates the cash flow for all trades of a trading record, including accrued cash flow of an open trade.
     * @param tradingRecord the trading record
     * @param finalIndex index up until cash flows of open trades are considered
     */
    private void calculate(TradingRecord tradingRecord, int finalIndex) {
        calculate(tradingRecord);

        // Add accrued cash flow of open trade
        if (tradingRecord.getCurrentTrade().isOpened()) {
            calculate(tradingRecord.getCurrentTrade(), finalIndex);
        }
    }

    /**
     * Fills with last value till the end of the series.
     */
    private void fillToTheEnd() {
        if (timeSeries.getEndIndex() >= values.size()) {
            Num lastValue = values.get(values.size() - 1);
            values.addAll(Collections.nCopies(timeSeries.getEndIndex() - values.size() + 1, lastValue));
        }
    }

    private int determineEndIndex(Trade trade, int finalIndex) {
        int idx = finalIndex;
        // After closing of trade, no further accrual necessary
        if (trade.getExit() != null) {
            idx = Math.min(trade.getExit().getIndex(), finalIndex);
        }
        // Accrual at most until the end of asset data
        if (idx > timeSeries.getEndIndex()) {
            idx = timeSeries.getEndIndex();
        }
        return idx;
    }
}