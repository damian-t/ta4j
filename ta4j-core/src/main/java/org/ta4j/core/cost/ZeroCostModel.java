package org.ta4j.core.cost;

import org.ta4j.core.Trade;
import org.ta4j.core.num.Num;

public class ZeroCostModel implements CostModel {

    /**
     * Constructor for a trading cost free model.
     *
     */
    public ZeroCostModel() {}

    public Num calculate(Trade trade, int currentIndex, Num currentPrice) {
        return currentPrice.numOf(0);
    }
}
