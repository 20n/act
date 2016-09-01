shinyUI(fluidPage(
  fluidRow(
    class = "logoAndTitle",
    column(2, imageOutput("logo", height = "100%")),
    column(8, headerPanel("Cost Model Explorer"), align = "center"),
    column(2)
  ),
  fluidRow(
    class = "inputs",
    column(2),
    column(4,
           wellPanel(
             sliderInput("market.price", "Market Price ($$/T)", 0, 100000, 7000, step = 1000),
             sliderInput("titer", "Titer (g/L)", 0, 170, 10, step = 1),
             selectInput("location", "Location", c("Germany", "Italy", "India", "China", "Midwest", "Mexico")),
             align="center"
           )
    ),
    column(4,
           wellPanel(
             selectInput("x.axis", "X Axis", c("InvestmentUSD", "InvestmentYears")),
             sliderInput("investment.usd.max.min", "Investment ($$M) Range", 0, 20, value=c(1,19), step = 1),
             sliderInput("investment.years.max.min", "Investment (Years) Range", 0, 10, value=c(1,9), step = 1),
             align="center"
           )
    ),
    column(2)
  ),
  fluidRow(
    class = "firstRowGraphs",
    column(1),
    column(5, plotOutput("plot1"), align="center"),
    column(5, plotOutput("plot2"), align="center"),
    column(1)
  ),
  fluidRow(
    class = "secondRowGraphs",
    column(1),
    column(5, plotOutput("plot3"), align="center"),
    column(5, plotOutput("plot4"), align="center"),
    column(1)
  ),
  fluidRow(
    class = "bottomLine",
    column(1),
    column(10, p(
      em("Systems Metabolic Engineering: Chemicals from renewable sources\' microorganisms"),
      'is listed in the top 10 in the',
      tags$a(href="http://www3.weforum.org/docs/GAC16_Top10_Emerging_Technologies_2016_report.pdf", "World Economic Forum\'s 2016 list of Top Emerging Technologies")), 
           align="center"),
    column(1)
  )
)
)