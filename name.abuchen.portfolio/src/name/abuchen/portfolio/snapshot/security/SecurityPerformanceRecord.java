package name.abuchen.portfolio.snapshot.security;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;

import name.abuchen.portfolio.math.IRR;
import name.abuchen.portfolio.math.LogarithmicRegression;
import name.abuchen.portfolio.model.Adaptable;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Transaction;
import name.abuchen.portfolio.model.Values;
import name.abuchen.portfolio.snapshot.PerformanceIndex;
import name.abuchen.portfolio.snapshot.ReportingPeriod;
import name.abuchen.portfolio.util.Dates;

public class SecurityPerformanceRecord implements Adaptable
{
    public enum Periodicity
    {
        UNKNOWN, NONE, INDEFINITE, ANNUAL, SEMIANNUAL, QUARTERLY, IRREGULAR;

        private static final ResourceBundle RESOURCES = ResourceBundle
                        .getBundle("name.abuchen.portfolio.snapshot.labels"); //$NON-NLS-1$

        public String toString()
        {
            return RESOURCES.getString("dividends." + name()); //$NON-NLS-1$
        }
    }

    private final Security security;
    private List<Transaction> transactions = new ArrayList<Transaction>();

    /**
     * internal rate of return of security {@link #calculateIRR()}
     */
    private double irr;

    /**
     * True time-weighted rate of return
     * {@link #calculatePerformance(ReportingPeriod)}
     */
    private double twror;

    /**
     * delta = market value + sells + dividends - purchase costs
     * {@link #calculateDelta()}
     */
    private long delta;

    /**
     * market value of holdings at end of period
     * {@link #addTransaction(Transaction)}
     */
    private long marketValue;

    /**
     * fifo cost of shares held {@link #calculateFifoCosts()}
     */
    private long fifoCost;

    /**
     * shares held {@link #calculateFifoCosts()}
     */
    private long sharesHeld;

    /**
     * cost per shares held {@link #calculateFifoCosts()}
     */
    private long fifoCostPerSharesHeld;

    /**
     * sum of all dividend payments {@link #calculateDividends()}
     */
    private long sumOfDividends;

    /**
     * number of dividend events during reporting period
     * {@link #calculateDividends()}
     */
    private int dividendEventCount;

    /**
     * last dividend payment in reporting period {@link #calculateDividends()}
     */
    private Date lastDividendPayment;

    private double irrdiv;
    private long divAmount;
    private long div12Shares;
    private long div12Cost;
    private long div12Amount;
    private long div24Amount;
    private long div60Amount;
    private long div120Amount;
    private long stockAmount; // Wert im Bestand
    private long stockShares; // Stücke im Bestand
    private Date dateFrom;
    private Date dateTo;
    private int divEventCount;
    private double divIncreasingRate;
    private double divIncreasingReliability;
    private int divIncreasingYears;

    private Periodicity periodicity = Periodicity.UNKNOWN;

    /* package */SecurityPerformanceRecord(Security security)
    {
        this.security = security;
    }

    public Security getSecurity()
    {
        return security;
    }

    public String getSecurityName()
    {
        return getSecurity().getName();
    }

    public double getIrr()
    {
        return irr;
    }

    public double getTrueTimeWeightedRateOfReturn()
    {
        return twror;
    }

    public long getDelta()
    {
        return delta;
    }

    public long getMarketValue()
    {
        return marketValue;
    }

    public long getFifoCost()
    {
        return fifoCost;
    }

    public long getSharesHeld()
    {
        return sharesHeld;
    }

    public long getFifoCostPerSharesHeld()
    {
        return fifoCostPerSharesHeld;
    }

    public long getSumOfDividends()
    {
        return sumOfDividends;
    }

    public int getDividendEventCount()
    {
        return dividendEventCount;
    }

    public Date getLastDividendPayment()
    {
        return lastDividendPayment;
    }

    public double getIrrDiv()
    {
        return irrdiv;
    }

    public double getTotalRateOfReturnDiv()
    {
        return sharesHeld > 0 ? (double) sumOfDividends / (double) fifoCost : 0;
    }

    public long getDivAmount()
    {
        return divAmount;
    }

    public long getDiv12Amount()
    {
        return div12Amount;
    }

    public long getDiv24Amount()
    {
        return div24Amount;
    }

    public long getDiv60Amount()
    {
        return div60Amount;
    }

    public long getDiv120Amount()
    {
        return div120Amount;
    }

    public long getDiv12PerShare()
    {
        return DividendTransaction.amountPerShare(div12Amount, div12Shares);
    }

    public long getCost12Amount()
    {
        return DividendTransaction.amountTimesShares(getStockPrice(), div12Shares);
    }

    public long getExpectedDiv12Amount()
    {
        return div24Amount;
    }

    public double getDivIncreasingRate()
    {
        return divIncreasingRate;
    }

    public double getDivIncreasingReliability()
    {
        return divIncreasingReliability;
    }

    public int getDivIncreasingYears()
    {
        return divIncreasingYears;
    }

    public long getDiv12MeanShares()
    {
        return div12Shares;
    }

    public long getStockAmount()
    {
        return stockAmount;
    }

    public long getStockShares()
    {
        return stockShares;
    }

    public long getStockPrice()
    {
        return DividendTransaction.amountPerShare(stockAmount, stockShares);
    }

    public Date getDateFrom()
    {
        return dateFrom;
    }

    public Date getDateTo()
    {
        return dateTo;
    }

    public int getDivEventCount()
    {
        return divEventCount;
    }

    public double getPersonalDiv()
    {
        long amountPerShare = getStockPrice();
        if (amountPerShare > 0)
        {
            return (double) div12Amount / (double) div12Cost;
        }
        else
        {
            return 0;
        }
    }

    public SecurityPerformanceRecord.Periodicity getPeriodicity()
    {
        return periodicity;
    }

    public int getPeriodicitySort()
    {
        return periodicity.ordinal();
    }

    public Boolean getHasDiv12()
    {
        switch (periodicity)
        {
            case UNKNOWN:
            case NONE:
                return false;
            default:
                return true;
        }
    }

    public List<Transaction> getTransactions()
    {
        return transactions;
    }

    @Override
    public <T> T adapt(Class<T> type)
    {
        return type == Security.class ? type.cast(security) : null;
    }

    /* package */void addTransaction(Transaction t)
    {
        transactions.add(t);

        if (t instanceof DividendFinalTransaction)
            marketValue = t.getAmount();
    }

    /* package */void calculate(Client client, ReportingPeriod period)
    {
        Transaction.sortByDate(transactions);

        if (!transactions.isEmpty())
        {
            calculateIRR();
            calculatePerformance(client, period);
            calculateDelta();
            calculateFifoCosts();
            calculateDividends();
            calculateIRRDiv();
            calculateDiv12(period.getEndDate());
        }
    }

    private void calculateIRR()
    {
        this.irr = Calculation.perform(IRRCalculation.class, transactions).getIRR();
    }

    private void calculatePerformance(Client client, ReportingPeriod period)
    {
        PerformanceIndex index = PerformanceIndex.forInvestment(client, security, period, new ArrayList<Exception>());
        double[] performance = index.getAccumulatedPercentage();
        this.twror = performance.length > 0 ? performance[performance.length - 1] : 0d;
    }

    private void calculateDelta()
    {
        this.delta = Calculation.perform(DeltaCalculation.class, transactions).getDelta();
    }

    private void calculateFifoCosts()
    {
        CostCalculation cost = Calculation.perform(CostCalculation.class, transactions);
        this.fifoCost = cost.getFifoCost();
        this.sharesHeld = cost.getSharesHeld();
        this.fifoCostPerSharesHeld = Math.round(fifoCost * Values.Share.factor() / (double) sharesHeld);
    }

    private void calculateDividends()
    {
        DividendCalculation dividends = Calculation.perform(DividendCalculation.class, transactions);
        this.sumOfDividends = dividends.getSum();
        this.dividendEventCount = dividends.getNumOfEvents();
        this.lastDividendPayment = dividends.getLastDividendPayment();
    }

    private void calculateIRRDiv()
    // setzt gleichzeitig die DivEventId's
    {
        List<Date> dates = new ArrayList<Date>();
        List<Double> values = new ArrayList<Double>();

        Calendar cal = Calendar.getInstance();
        Date tDateFinal = cal.getTime();
        stockAmount = 0;
        stockShares = 0;
        long poolAmount = 0;
        long poolShares = 0;
        divEventCount = 0;

        Date dCurr, dLast = null;
        for (Transaction t : transactions)
        {

            cal.setTime(t.getDate());
            long tAmount = 0;
            Date tDate = t.getDate();

            if (t instanceof DividendInitialTransaction)
            {
                DividendInitialTransaction dit = (DividendInitialTransaction) t;
                // Eingangsbuchung wie buy.
                // Kaufpreise und -mengen kumulieren, neuer
                // durchschnittlicher Einstandspreis

                stockAmount += dit.getAmount();
                stockShares += dit.getPosition().getShares();
                if (stockShares <= 0)
                {
                    // wenn pt.getAmount immer > 0 ist, kann das nicht
                    // passieren
                    throw new UnsupportedOperationException();
                }

                tAmount = -dit.getAmount();
            }
            else if (t instanceof DividendFinalTransaction)
            {
                // Abschlussbuchungen ignorieren, statt dessen ganz am Ende
                // stockamount buchen
                // aber: Datum für Abschlussbuchung merken
                tDateFinal = tDate;
            }
            else if (t instanceof DividendTransaction)
            {
                // "normale" Dividenden
                DividendTransaction dt = (DividendTransaction) t;
                tAmount = dt.getAmount();

                // nur bei regulären Dividenden: DivEventId setzen, damit
                // mehrere Buchungen zusammengefasst werden können
                if (dt.getShares() > 0)
                {

                    // prüfen, ob die Dividende zum gleichen Zahlungstag
                    // gehört
                    dCurr = dt.getDate();
                    if ((dLast == null) || (Dates.daysBetween(dLast, dCurr) > 30))
                    {
                        divEventCount++;
                    }
                    dLast = dCurr;

                    // hier wird nun die BlockId gesetzt
                    dt.setDivEventId(divEventCount);
                }

            }
            else if (t instanceof PortfolioTransaction)
            {
                // Bestandsveränderungen
                PortfolioTransaction pt = (PortfolioTransaction) t;
                long shares = pt.getShares();
                long amount = pt.getAmount();
                switch (pt.getType())
                {
                    case BUY:
                    case DELIVERY_INBOUND:
                        // Kaufpreise und -mengen kumulieren
                        if (poolShares > 0)
                        {
                            // der Kaufpreis wird anteilig um den im Pool
                            // gespeicherten Gewinn/Verlust reduziert
                            long ps = Math.min(shares, poolShares);
                            long pa = DividendTransaction.amountTimesShares(
                                            DividendTransaction.amountPerShare(poolAmount, poolShares), ps);

                            poolShares -= ps;
                            poolAmount -= pa;
                            if (poolShares == 0)
                            {
                                // Restwert komplett ausbuchen, sonst
                                // bleiben die Rundungsdifferenzen auf
                                // poolAmount stehen
                                pa += poolAmount;
                                poolAmount = 0;
                            }
                            amount -= pa;
                        }

                        stockShares += shares;
                        stockAmount += amount;
                        tAmount = -amount;
                        break;
                    case SELL:
                    case DELIVERY_OUTBOUND:
                        // Kaufpreise um mittleren Einstand reduzieren,
                        // Differenz zum Verkaufspreis im Pool bunkern
                        // Kaufmengen kumulieren
                        long cost = DividendTransaction.amountTimesShares(getStockPrice(), shares);
                        long profit = amount - cost;
                        stockShares -= shares;
                        poolShares += shares;
                        stockAmount -= cost;
                        poolAmount += profit;
                        if (stockShares == 0)
                        {
                            // Restwert komplett ausbuchen, sonst bleiben
                            // die Rundungsdifferenzen auf stockamount
                            // stehen
                            poolAmount += stockAmount;
                            stockAmount = 0;
                        }

                        if (stockShares < 0)
                        {
                            // wenn pt.getAmount immer > 0 ist, kann das
                            // nicht passieren
                            throw new UnsupportedOperationException();
                        }
                        tAmount = cost;
                        break;
                    case TRANSFER_OUT:
                    case TRANSFER_IN:
                        tAmount = 0;
                        // wie müssen Ein/Auslieferungen dividendenmäßig
                        // gebucht werden ?
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }
            }
            else
            {
                throw new UnsupportedOperationException();
            }
            if (tAmount != 0)
            {
                // nur relevante Positionen buchen
                values.add(tAmount / Values.Amount.divider());
                dates.add(tDate);
            }

        }

        // statt des Marktwertes Wert muss hier der Einstandswert gebucht
        // werden
        values.add(stockAmount / Values.Amount.divider());
        dates.add(tDateFinal);

        this.irrdiv = IRR.calculate(dates, values);
    }

    private int getEventsPerYear()
    {
        switch (periodicity)
        {
            case ANNUAL:
                return 1;
            case SEMIANNUAL:
                return 2;
            case QUARTERLY:
                return 4;
            default:
                return 0;
        }
    }

    // für Voraussagen mindestens 3 Jahre durchgehende Zahlungen
    private static int cMinYearsForDIR = 3;

    private void calculateDIR()
    // Berechnung mit logarithmischer Regression
    {
        int eBlock = getEventsPerYear();
        if (eBlock == 0)
            return;

        // jetzt die Jahresdividenden aufbauen
        divIncreasingRate = 0;
        divIncreasingYears = 0;
        divIncreasingReliability = 0;

        LogarithmicRegression logRegression = new LogarithmicRegression();

        Date dBase = null;
        Date dCurr = null;
        int eCurr = 0;
        long divAmount = 0;
        long divShares = 0;
        double year = 0;
        String x = "";
        for (Transaction t : transactions)
        {

            if (t instanceof DividendTransaction)
            {
                DividendTransaction dt = (DividendTransaction) t;
                int eNext = dt.getDivEventId();
                Date dNext = dt.getDate();

                if (dBase == null)
                    dBase = dNext;
                if (dCurr == null)
                    dCurr = dNext;

                if (eNext != eCurr)
                {
                    if ((divAmount != 0) && (divShares != 0))
                    {
                        // neuer Block - aufgelaufene Summe wird zugefügt
                        // positiven Wert zufügen
                        year = (double) Dates.daysBetween(dBase, dCurr) / 365.25;
                        long d = DividendTransaction.amountPerShare(divAmount, divShares);
                        logRegression.add(year, d);

                        x = x.concat(String.format("(%d;%.2f;%.2f);", eNext - 1, year, (double) d / 100));
                    }

                    // nächsten Zeitraum beginnen
                    eCurr = eNext;
                    dCurr = dNext;
                    divAmount = 0;
                    divShares = 0;
                }

                if (dt.getShares() > 0)
                {
                    divAmount += dt.getAmount();
                    divShares += dt.getShares();
                }
                else
                {
                    // Dividendenbuchungen mit Menge<=0 ignorieren -
                    // sind Sonderzahlungen. Stark streuende sind jetzt egal
                }

            }
        }

        if (year < cMinYearsForDIR)
            return; // Periode nicht lang genug

        // abschließend mitteln

        double rLog = logRegression.getKorrel();
        double rate = Math.exp(logRegression.getSlopeX()) - 1;
        if (rate > 0 || true)
        {
            divIncreasingRate = rate;
            divIncreasingYears = (int) Math.round(year);
            divIncreasingReliability = rLog;
        }
    }

    private void calculateDiv12(Date endDate)
    {
        divAmount = 0;
        div12Cost = 0;
        div12Shares = 0;
        div12Amount = 0;
        div24Amount = 0;
        div60Amount = 0;
        div120Amount = 0;
        dateFrom = null;
        dateTo = null;
        periodicity = Periodicity.NONE;
        divIncreasingRate = 0;

        // Dividenden summieren, dabei neueste Dividendenzahlung finden.
        // Da das Datum der Zahlungen von Jahr zu Jahr, von Bank zu Bank
        // variiert,
        // wird hier das Jahr um die Hälfte eines Quartals verlängert in der
        // Hoffnung,
        // das die Schwankungen nicht größer sind: 1 Jahr + 1½ Monate = 410
        // Tage
        // Beispiel: Zahlungen jeweils am 15.Feb, 15.Mai, 15.Aug. und
        // 15.Nov.2012, endDate ist 14.Feb.2013 unmittelbar vor der
        // nächsten geplanten Zahlung. Ist ein Zeitraum von 364 Tagen.
        // Vieleicht war erste Zahlung ja schon am 31.Jan.2012,
        // also lieber 6 Wochen Puffer berücksichtigen
        DividendTransaction dt = null;
        Date refDate = Dates.addDays(endDate, -410);

        for (Transaction t : transactions)
        {
            if (t instanceof DividendTransaction)
            {
                dt = (DividendTransaction) t;
                divAmount += dt.getAmount();
                Date dtd = dt.getDate();
                if (dtd.before(refDate))
                {
                    // alle Dividendenzahlungen deutlich älter als 1 Jahr
                    // ignorieren
                }
                else if ((dateTo == null) || (dtd.after(dateTo)))
                {
                    dateTo = dtd;
                }
            }
        }

        if (dateTo == null)
            return;
        if (stockAmount == 0)
            return;

        // Abgrenzungsdatum für ein Jahr: alle Zahlungen innerhalb der 10½
        // Monate = 365-45 = 320 Tage
        // beginnend mit der letzten Zahlung, die im vorigen Durchlauf
        // gefunden wurde.
        // Argumentation z.B. für Quartalszahlungen: eigentlich müssen die
        // letzten 4 Zahlungen innerhalb von 9 Monaten
        // stattgefunden haben. Man benötigt jedoch ein Sicherheitspolster,
        // um Schwankungen auszugleichen.
        // Beispiel: Zahlungen jeweils am 15.Feb, 15.Mai, 15.Aug. und 15.Nov
        // 2012 ist ein Zeitraum von 270 Tagen
        dateFrom = Dates.addDays(dateTo, -320);

        // Gesamtzeitraum für mittlere Stückzahl
        long sumShare = 0;
        long medShare = 0;
        long medDays = 0;
        Date dCurr = null;
        Date dLast = Dates.addDays(dateTo, -365);
        int bCurr = 0;
        int bLast = 0;

        for (Transaction t : transactions)
        {
            if (t instanceof DividendTransaction)
            {
                dt = (DividendTransaction) t;
                dCurr = dt.getDate();
                bCurr = dt.getDivEventId();
                if (bCurr == 0)
                {
                    // Sonderdividenden ignorieren
                }
                else if (dCurr.after(dateFrom))
                {
                    div12Amount += dt.getAmount();
                    dt.setIsDiv12(true);
                    long days = Dates.daysBetween(dLast, dCurr);
                    medShare += days * sumShare;
                    medDays += days;
                    dLast = dCurr;
                }
                else
                {
                    // letzte BlockId vor dem 12-Monatszeitraum merken
                    bLast = dt.getDivEventId();
                }
            }
            else if (t instanceof DividendInitialTransaction)
            {
                sumShare += ((DividendInitialTransaction) t).getPosition().getShares();
            }
            else if (t instanceof PortfolioTransaction)
            {
                PortfolioTransaction pt = (PortfolioTransaction) t;
                switch (pt.getType())
                {
                    case BUY:
                    case TRANSFER_IN:
                        sumShare += pt.getShares();
                        break;
                    case SELL:
                    case TRANSFER_OUT:
                        sumShare -= pt.getShares();
                        break;
                    default:
                }
            }
        }
        if (medDays > 0)
        {
            div12Shares = medShare / medDays;
            div12Cost = DividendTransaction.amountTimesShares(getStockPrice(), div12Shares);

            int nEvents = bCurr - bLast;
            if ((nEvents > 0) && (bLast == 0))
            {
                // ohne Zahlung in der Vorperiode kann die Periodizität
                // nicht sicher bestimmt werden
                periodicity = Periodicity.INDEFINITE;
            }
            else
            {
                switch (nEvents)
                {
                    case 0:
                    {
                        periodicity = Periodicity.NONE;
                        break;
                    }
                    case 1:
                    {
                        periodicity = Periodicity.ANNUAL;
                        break;
                    }
                    case 2:
                    {
                        periodicity = Periodicity.SEMIANNUAL;
                        break;
                    }
                    case 4:
                    {
                        periodicity = Periodicity.QUARTERLY;
                        break;
                    }
                    default:
                    {
                        periodicity = Periodicity.IRREGULAR;
                        break;
                    }
                }
            }
        }

        // calculations for expected values
        switch (periodicity)
        {
            case ANNUAL:
            case SEMIANNUAL:
            case QUARTERLY:
            case INDEFINITE:
            {
                div24Amount = DividendTransaction.amountTimesShares(getDiv12PerShare(), stockShares);
                break;
            }
            case IRREGULAR:
                break; // could contain extraordinarily payments - so better
                       // ignore this
            default:
                // UNKNOWN, NONE,
                break; // no values;
        }

        // calculate dividend increasing rate
        calculateDIR();

        if (divIncreasingYears > 0)
        {
            // Projektion: Dividende wird jedes Jahr um den verlässlichen
            // Teil der Steigerungsrate erhöht
            div60Amount = Math.round((double) div24Amount
                            * Math.pow(1 + divIncreasingRate * divIncreasingReliability, 5));
            div120Amount = Math.round((double) div24Amount
                            * Math.pow(1 + divIncreasingRate * divIncreasingReliability, 10));
        }
        else
        {
            // keine Angabe: die Höhe der Dividende ändert sich nicht
            div60Amount = div24Amount;
            div120Amount = div24Amount;
        }
    }
}
