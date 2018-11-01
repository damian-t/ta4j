package org.ta4j.core.cost;

import org.ta4j.core.Trade;
import org.ta4j.core.num.Num;

import java.io.Serializable;


public interface CostModel extends Serializable {

    /**
     * @param trade the trade
     * @param finalIndex final index of consideration for open trades
     * @return Calculates the trading cost of a single trade
     */

    Num calculate(Trade trade, int finalIndex);

    Num calculate(Num price, Num amount);
}