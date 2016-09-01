# ui.R defines the layout and display of the different app components.
# It is organized in rows and columns.
# Add as many rows as you'd like, columns sizes should add up to 12 within a row.

# The UI can render outputs computed by the server
# (ex: plotOutput("plot1") plots the output "plot1" computed by server.R)
# or define inputs that will be communicated with the server (ex: sliderInput("market.price", ...))

kAllLocations = c("Germany", "Italy", "India", "China", "Midwest", "Mexico")

kBottomMessage = p(
  em("Systems Metabolic Engineering: Chemicals from renewable sources\' microorganisms"),
  'is listed in the top 10 in the',
  tags$a(href="http://www3.weforum.org/docs/GAC16_Top10_Emerging_Technologies_2016_report.pdf",
         "World Economic Forum\'s 2016 list of Top Emerging Technologies")
)

kXAxisOptions = c("InvestmentUSD", "InvestmentYears")

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
             sliderInput("market.price", "Market Price ($$/T)", min=0, max=100000, value=7000, step=1000),
             sliderInput("titer", "Titer (g/L)", min=0, max=170, value=10, step=1),
             selectInput("location", "Location", kAllLocations),
             align="center"
           )
    ),
    column(4,
           wellPanel(
             selectInput("x.axis", "X Axis", kXAxisOptions),
             sliderInput("investment.usd.max.min", "Investment ($$M) Range", min=0, max=20, value=c(1,19), step=1),
             sliderInput("investment.years.max.min", "Investment (Years) Range", min=0, max=10, value=c(1,9), step=1),
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
    column(10, kBottomMessage,
           align="center"),
    column(1)
  )
)
)
