/*
 * Copyright 2024-2025 Embabel Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.example.travel.agent

import com.embabel.tripper.agent.Day
import com.embabel.tripper.agent.JourneyTravelBrief
import com.embabel.tripper.agent.ProposedTravelPlan
import com.embabel.tripper.agent.TravelPlan
import com.embabel.tripper.agent.Travelers
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class TravelPlanTest {

    @Nested
    inner class Mapping {

        @Test
        fun `extracts readable stay location from map friendly value`() {
            val day = Day(LocalDate.of(2020, 1, 1), "Dijon,+France")

            assertEquals("Dijon", day.stayingAt)
        }

        @Test
        fun `builds google maps direction url from distinct day locations`() {
            val proposedTravelPlan = ProposedTravelPlan(
                title = "One day trip",
                plan = "Have a good time",
                days = listOf(
                    Day(LocalDate.of(2019, 12, 31), "Paris,+France"),
                    Day(LocalDate.of(2020, 1, 1), "Dijon,+France"),
                    Day(LocalDate.of(2020, 1, 2), "Dijon,+France"),
                    Day(LocalDate.of(2020, 1, 3), "Beaune,+France"),
                ),
                imageLinks = emptyList(),
                pageLinks = emptyList(),
                videoLinks = emptyList(),
                countriesVisited = listOf("France"),
            )
            val travelPlan = TravelPlan(
                brief = JourneyTravelBrief(
                    from = "Paris",
                    to = "Beaune",
                    transportPreference = "driving",
                    brief = "A short trip through Burgundy",
                    departureDate = LocalDate.of(2019, 12, 31),
                    returnDate = LocalDate.of(2020, 1, 3),
                ),
                proposal = proposedTravelPlan,
                stays = emptyList(),
                travelers = Travelers(emptyList()),
            )

            assertEquals(
                "https://www.google.com/maps/dir/Paris%2C%2BFrance/Dijon%2C%2BFrance/Beaune%2C%2BFrance",
                travelPlan.journeyMapUrl,
            )
        }
    }

}
