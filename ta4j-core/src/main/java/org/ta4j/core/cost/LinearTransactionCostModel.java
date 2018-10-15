package org.ta4j.core.cost;

import org.ta4j.core.Order;
import org.ta4j.core.Trade;
import org.ta4j.core.num.Num;

public class LinearTransactionCostModel implements CostModel {

    /**
     * Slope of the linear model - fee per trade
     */
    private double feePerTrade;

    /**
     * Constructor.
     * (feePerTrade * x)
     * @param feePerTrade the feePerTrade coefficient (e.g. 0.005 for 0.5% per {@link Order order})
     */
    public LinearTransactionCostModel(double feePerTrade) {
        this.feePerTrade = feePerTrade;
    }


//    /**
//     * Calculates the transaction cost of a trade.
//     * @param trade the trade
//     * @param currentIndex current bar index (for open trades)
//     * @param currentPrice price of the current bar (for open trades)
//     * @return the absolute order cost
//     */
//    public Num calculate(Trade trade, int currentIndex, Num currentPrice) {
//        Num totalTradeCost = currentPrice.numOf(0);
//        Order entryOrder = trade.getEntry();
//        if (entryOrder != null) {
//            // transaction costs of entry order
//            totalTradeCost = getOrderCost(entryOrder.getValue());
//            if (trade.getExit() != null) {
//                // effective amount of entry order is adjusted for transaction costs
//                // amt_real = amt_ordered * (1 - p_exit/p_entry * fee)
//                Num exitPrice = trade.getExit().getPrice();
//                Num newTradedAmount = entryOrder.getAmount().minus(totalTradeCost.dividedBy(exitPrice));
//                Num newTradedValue = newTradedAmount.multipliedBy(exitPrice);
//                // add transaction costs of exit order
//                totalTradeCost = totalTradeCost.plus(getOrderCost(newTradedValue));
//            }
//        }
//        return totalTradeCost;
//    }

    /**
     * Calculates the transaction cost of a trade.
     * @param trade the trade
     * @param currentIndex current bar index (for open trades)
     * @param currentPrice price of the current bar (for open trades)
     * @return the absolute order cost
     */
    public Num calculate(Trade trade, int currentIndex, Num currentPrice) {
        Num totalTradeCost = currentPrice.numOf(0);
        Order entryOrder = trade.getEntry();
        if (entryOrder != null) {
            // transaction costs of entry order
            totalTradeCost = entryOrder.getCost();
            if (trade.getExit() != null) {
                totalTradeCost = trade.getExit().getCost();
            }
        }
        return totalTradeCost;
    }

    /**
     * @param price execution price
     * @param amount order amount
     * @return the absolute order cost
     */
    public Num calculate(Num price, Num amount) {
        return amount.numOf(feePerTrade).multipliedBy(price).multipliedBy(amount);
    }
}
