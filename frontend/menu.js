'use strict';
// The restaurant's food and drinks menu, shown on the homepage.
// It is a printed-menu style listing only: there is no online ordering, cart or checkout.
// Illustrations are original artwork drawn for this project (frontend/assets/menu/*.svg).
const MENU = [
  { id: 'filipino', title: 'Filipino', intro: 'Homestyle dishes from Luzon, Visayas and Mindanao.', items: [
    { name: 'Chicken Adobo', price: 320, image: 'chicken-adobo', alt: 'Chicken adobo in dark soy-vinegar sauce beside steamed rice',
      description: 'Chicken braised in soy sauce, cane vinegar, garlic and bay leaf, served with steamed rice.' },
    { name: 'Sinigang na Baboy', price: 380, image: 'sinigang-na-baboy', alt: 'Bowl of pork sinigang with kangkong, radish and tomato',
      description: 'Pork ribs in a sour tamarind broth with kangkong, radish, tomato and green chili.' },
    { name: 'Kare-Kare', price: 450, image: 'kare-kare', alt: 'Clay pot of kare-kare with vegetables and a side of shrimp paste',
      description: 'Oxtail and vegetables in a rich peanut sauce, with sautéed shrimp paste on the side. Contains peanuts and shellfish.' },
    { name: 'Lechon Kawali', price: 390, image: 'lechon-kawali', alt: 'Slices of crispy pork belly with liver sauce, tomato and cucumber',
      description: 'Crispy deep-fried pork belly with house liver sauce, tomato and cucumber.' },
  ] },
  { id: 'chinese', title: 'Chinese', intro: 'Dim sum, wok-fried rice and noodle favorites.', items: [
    { name: 'Pork Siomai', price: 160, image: 'pork-siomai', alt: 'Four pork siomai dumplings in a bamboo steamer with soy and chili',
      description: 'Four steamed pork and shrimp dumplings with calamansi-soy and chili garlic. Contains shellfish.' },
    { name: 'Yang Chow Fried Rice', price: 240, image: 'yang-chow-fried-rice', alt: 'Bowl of fried rice with shrimp, egg, peas and carrots',
      description: 'Wok-fried rice with shrimp, Chinese sausage, egg, peas and carrots. Good for two. Contains shellfish and egg.' },
    { name: 'Sweet and Sour Pork', price: 340, image: 'sweet-and-sour-pork', alt: 'Plate of glazed sweet and sour pork with bell peppers and pineapple',
      description: 'Crisp pork tossed in a tangy sweet and sour glaze with bell peppers and pineapple.' },
    { name: 'Beef Wonton Noodle Soup', price: 280, image: 'beef-wonton-noodle-soup', alt: 'Bowl of noodle soup with beef, wontons and bok choy',
      description: 'Egg noodles in clear beef broth with braised beef, pork wontons and bok choy. Contains egg and wheat.' },
  ] },
  { id: 'japanese', title: 'Japanese', intro: 'Ramen, fresh cuts and crisp fried plates.', items: [
    { name: 'Tonkotsu Ramen', price: 390, image: 'tonkotsu-ramen', alt: 'Bowl of tonkotsu ramen with chashu pork, soft egg and nori',
      description: 'Creamy pork bone broth with noodles, chashu pork, soft-boiled egg, nori and scallions. Contains egg and wheat.' },
    { name: 'Salmon Sashimi', price: 420, image: 'salmon-sashimi', alt: 'Six slices of salmon sashimi on a slate plate with wasabi and ginger',
      description: 'Six slices of fresh salmon with wasabi, pickled ginger and soy sauce. Served raw.' },
    { name: 'Chicken Katsu Curry', price: 360, image: 'chicken-katsu-curry', alt: 'Sliced breaded chicken cutlet over Japanese curry and rice',
      description: 'Panko-breaded chicken cutlet over mild Japanese curry and steamed rice. Contains wheat and egg.' },
    { name: 'Ebi Tempura', price: 380, image: 'ebi-tempura', alt: 'Five pieces of shrimp tempura with dipping sauce',
      description: 'Five lightly battered prawns with tentsuyu dipping sauce and grated radish. Contains shellfish and wheat.' },
  ] },
  { id: 'drinks', title: 'Drinks', intro: 'Something cold to go with your meal.', items: [
    { name: 'Calamansi Juice', price: 90, image: 'calamansi-juice', alt: 'Glass of iced calamansi juice with a calamansi slice',
      description: 'Freshly squeezed calamansi, lightly sweetened and served over ice.' },
    { name: 'Sago’t Gulaman', price: 85, image: 'sago-at-gulaman', alt: 'Glass of sago’t gulaman with tapioca pearls and jelly cubes',
      description: 'Brown sugar syrup drink with tapioca pearls and jelly cubes.' },
    { name: 'Iced Jasmine Tea', price: 80, image: 'iced-jasmine-tea', alt: 'Glass of iced jasmine tea with jasmine flowers',
      description: 'Brewed jasmine tea served cold, unsweetened or with syrup on request.' },
    { name: 'Matcha Latte', price: 150, image: 'matcha-latte', alt: 'Glass of iced matcha latte with layers of green tea and milk',
      description: 'Japanese green tea whisked with milk, served iced or hot. Contains milk.' },
  ] },
];
const menuPrice = value => new Intl.NumberFormat('en-PH', { style: 'currency', currency: 'PHP', maximumFractionDigits: 0 }).format(value);
/** Returns the menu section markup. Every dish is a list item with an image, name, description and price. */
function menuSection() {
  const group = g => `<section class="menu-group" id="menu-${g.id}" aria-labelledby="menu-${g.id}-title" tabindex="-1"><div class="menu-group-head"><h3 id="menu-${g.id}-title">${g.title}</h3><p>${g.intro}</p></div><ul class="menu-grid">${g.items.map(item =>
    `<li class="menu-item"><img src="/assets/menu/${item.image}.svg" alt="${item.alt}" width="400" height="300" loading="lazy" decoding="async"><div class="menu-item-body"><div class="menu-item-title"><h4>${item.name}</h4><span class="menu-price"><span class="visually-hidden">Price: </span>${menuPrice(item.price)}</span></div><p>${item.description}</p></div></li>`).join('')}</ul></section>`;
  return `<section id="menu" class="menu-section" aria-labelledby="menu-title"><div class="eyebrow">Our menu</div><h2 id="menu-title">Three kitchens. One table.</h2><p class="menu-lead">Filipino, Chinese and Japanese dishes, cooked to order and served at your table.</p><nav class="menu-nav" aria-label="Menu categories">${MENU.map(g => `<button type="button" class="button secondary small" data-menu-jump="menu-${g.id}">${g.title}</button>`).join('')}</nav>${MENU.map(group).join('')}<p class="menu-note">Prices are in Philippine pesos (₱) per serving and include VAT. The menu is served in the restaurant: order with our staff at your table. Online food ordering is not available. Please tell us about allergies when you reserve. Illustrations are for presentation; actual plating may differ.</p></section>`;
}
/** Category buttons move focus to their group without changing the page address. */
function bindMenu(root) {
  root.querySelectorAll('[data-menu-jump]').forEach(button => button.addEventListener('click', () => {
    const target = document.getElementById(button.dataset.menuJump);
    if (target) { target.scrollIntoView({ behavior: 'smooth', block: 'start' }); target.focus({ preventScroll: true }); }
  }));
}
