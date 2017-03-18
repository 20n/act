##########################################################################
#                                                                        #
#  This file is part of the 20n/act project.                             #
#  20n/act enables DNA prediction for synthetic biology/bioengineering.  #
#  Copyright (C) 2017 20n Labs, Inc.                                     #
#                                                                        #
#  Please direct all queries to act@20n.com.                             #
#                                                                        #
#  This program is free software: you can redistribute it and/or modify  #
#  it under the terms of the GNU General Public License as published by  #
#  the Free Software Foundation, either version 3 of the License, or     #
#  (at your option) any later version.                                   #
#                                                                        #
#  This program is distributed in the hope that it will be useful,       #
#  but WITHOUT ANY WARRANTY; without even the implied warranty of        #
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         #
#  GNU General Public License for more details.                          #
#                                                                        #
#  You should have received a copy of the GNU General Public License     #
#  along with this program.  If not, see <http://www.gnu.org/licenses/>. #
#                                                                        #
##########################################################################

library(mongolite)
library(wordcloud)

kArgs <- commandArgs(trailingOnly = TRUE)

input.inchi <- kArgs[1]
output.file <- kArgs[2]
host <- kArgs[3]
port <- kArgs[4]
databaseName <- kArgs[5] # "SHOULD_COME_FROM_CMDLINE" # "jarvis_2016-12-09"

options(mongodb = list("host" = paste(host, port, sep = ":")))
collectionName <- "chemicals"

db <- mongo(collection = collectionName,
            url = sprintf("mongodb://%s/%s",
                          options()$mongodb$host,
                          databaseName)
)


result <- db$find(sprintf('{"InChI": "%s"}', input.inchi))


usage.terms.urls <- as.data.frame(result$xref$BING$metadata$usage_terms)

terms <- usage.terms.urls$usage_term
freq <- sapply(usage.terms.urls$urls, length)

pal2 <- brewer.pal(8,"Dark2")

set.seed(27)

png(output.file)
wordcloud(terms,freq, scale=c(4,.5),min.freq=1, max.words=30, random.order=FALSE, rot.per=0, colors=pal2)
dev.off()
