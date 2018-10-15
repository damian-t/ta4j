package org.ta4j.core.cost;

import org.ta4j.core.Order;
import org.ta4j.core.Trade;
import org.ta4j.core.num.Num;

public class LinearBorrowingCostModel implements CostModel {

    /**
     * Slope of the linear model - fee per period
     */
    private double feePerPeriod;

    /**
     * Constructor.
     * (feePerPeriod * nPeriod)
     * @param feePerPeriod the coefficient (e.g. 0.0001 for 1bp per period)
     */
    public LinearBorrowingCostModel(double feePerPeriod) {
        this.feePerPeriod = feePerPeriod;
    }

    // TODO: ensure, raise Exception
    public Num calculate(Num price, Num amount) {
        return price.numOf(0);
    }

    /**
     * Calculates the borrowing cost of a trade.
     * @param trade the trade
     * @param finalIndex final bar index to be considered (for open trades)
     * @param finalPrice price of the final bar to be considered (for open trades)
     * @return the absolute order cost
     */
    public Num calculate(Trade trade, int currentIndex) {
        Order entryOrder = trade.getEntry();
        Order exitOrder = trade.getExit();
        Num borrowingCost = trade.getEntry().getPrice().numOf(0);

        if (entryOrder != null && entryOrder.getType().equals(Order.OrderType.SELL) && entryOrder.getAmount() != null) {
            int tradingPeriods = 0;
            if (trade.isClosed()) {
                tradingPeriods = exitOrder.getIndex() - entryOrder.getIndex();
            } else if (trade.isOpened()) {
                tradingPeriods = currentIndex - entryOrder.getIndex();
            }
            borrowingCost = getHoldingCostForPeriods(tradingPeriods, trade.getEntry().getValue());
        }
        return borrowingCost;
    }

    /**
     * @param tradingPeriods a trade order
     * @param tradedValue the traded value of the order
     * @return the absolute order cost
     */
    private Num getHoldingCostForPeriods(int tradingPeriods, Num tradedValue) {
        return tradedValue
                .multipliedBy(tradedValue.numOf(tradingPeriods)
                        .multipliedBy(tradedValue.numOf(feePerPeriod)));
    }
}
