'use strict';
// Restaurant experience layer: homepage with menu, table booking with package prices, manual GCash payment,
// partial cancellation, and the staff tools for payments, refunds and prices. It builds on the API client in app.js.

// ---------- Shared helpers ----------
const money = value => new Intl.NumberFormat('en-PH',{style:'currency',currency:'PHP'}).format(value);
const assistanceOptions=['Wheelchair access','Baby/high chair','Parking space','Special occasion','Quiet area','Other assistance'];
const stayLabel=minutes=>{const h=Math.floor(minutes/60),m=minutes%60;return [h?`${h} hour${h>1?'s':''}`:'',m?`${m} min`:''].filter(Boolean).join(' ');};
const stayOptions=(selected)=>(info.stayOptions||[info.settings.durationMinutes]).map(m=>`<option value="${m}" ${m===selected?'selected':''}>${stayLabel(m)}</option>`).join('');
const stayRange=()=>{const o=info.stayOptions||[info.settings.durationMinutes];return `${stayLabel(o[0])} to ${stayLabel(o[o.length-1])}`;};
const plural=(count,word)=>`${count} ${word}${count===1?'':'s'}`;

// Time. Reservation dates and times are restaurant wall-clock values sent by the server and are shown as-is.
// Nothing here reads the device clock or timezone to decide a date, a time or what can be booked.
const zoneShort=()=>`UTC${info.utcOffset}`;
const visitTime=r=>`${timeLabel(r.startsAt.slice(11))} – ${timeLabel(r.endsAt.slice(11))}`;
/** Formats a server timestamp (an instant) in the restaurant's timezone, whatever the device is set to. */
const instantLabel=iso=>iso?new Intl.DateTimeFormat('en-PH',{timeZone:info.zone,dateStyle:'medium',timeStyle:'short'}).format(new Date(iso)):'';

// Philippine mobile numbers. The field holds the 10 digits after +63; the server stores +639XXXXXXXXX.
// The same rule is enforced on the server (PhoneNumbers.java).
function phoneDigits(value){let d=String(value??'').replace(/\D/g,'');if(d.length>10&&d.startsWith('63'))d=d.slice(2);d=d.replace(/^0+/,'');return d.slice(0,10);}
const phoneValid=value=>/^9\d{9}$/.test(phoneDigits(value));
const toE164=value=>'+63'+phoneDigits(value);
/** Shows stored 09XXXXXXXXX and +639XXXXXXXXX numbers the same way; anything unrecognised is shown unchanged. */
const phoneDisplay=value=>{const d=phoneDigits(value);return /^9\d{9}$/.test(d)?`+63 ${d.slice(0,3)} ${d.slice(3,6)} ${d.slice(6)}`:String(value??'');};

const paymentLabels={NOT_REQUIRED:'No payment needed',UNPAID:'Payment due',PENDING:'Payment pending verification',PAID:'Paid',REJECTED:'Payment not verified'};
// Bookings paid in the earlier classroom simulator are labelled as simulated: no money was received for them.
const simulatedOnly=r=>r.paymentStatus==='PAID'&&r.payments.filter(p=>p.status==='PAID').every(p=>p.method==='DEMO_SIMULATED');
const paymentTag=r=>simulatedOnly(r)?'<span class="status pay-pending">Simulated payment (no money received)</span>':`<span class="status pay-${r.paymentStatus.toLowerCase()}">${paymentLabels[r.paymentStatus]||pretty(r.paymentStatus)}</span>`;
const methodLabel=m=>({GCASH_MANUAL:'GCash transfer, verified by staff',DEMO_SIMULATED:'Old classroom simulation (no money received)'})[m]||m;
const canPay=r=>r.status==='PENDING'&&['UNPAID','REJECTED'].includes(r.paymentStatus)&&Number(r.money.due)>0;
const activeItems=r=>r.items.filter(i=>i.status==='ACTIVE');
let pricingInfo=null;
async function loadPricing(){pricingInfo=await api('/pricing');return pricingInfo;}
function itemLines(r){
 return `<div class="table-wrap"><table class="lines"><caption class="visually-hidden">Tables and package prices</caption><thead><tr><th scope="col">Table</th><th scope="col">Seats</th><th scope="col">Package price</th><th scope="col">Status</th></tr></thead><tbody>${r.items.map(i=>`<tr class="${i.status==='ACTIVE'?'':'line-off'}"><td>${esc(i.tableNumber)}</td><td>${i.capacity}</td><td>${money(i.price)}</td><td>${i.status==='ACTIVE'?'Booked':i.status==='CANCELLED'?`Cancelled ${esc(instantLabel(i.cancelledAt))}${Number(i.refundableAmount)>0?` · ${money(i.refundableAmount)} refundable`:' · not refundable'}`:'Replaced before payment'}</td></tr>`).join('')}</tbody></table></div>`;
}
function moneySummary(r){
 const m=r.money,row=(label,value,show=true)=>show?`<dt>${label}</dt><dd>${money(value)}</dd>`:'';
 return `<dl class="money">${row('Original total',m.originalTotal)}${row('Current total',m.currentTotal,Number(m.currentTotal)!==Number(m.originalTotal))}${row('Verified payments',m.paid)}${row('Amount due',m.due,Number(m.due)>0)}${row('Refund owed to guest',m.refundDue,Number(m.refundDue)>0)}${row('Refund being sent',m.refundPending,Number(m.refundPending)>0)}${row('Refunded',m.refunded,Number(m.refunded)>0)}</dl>`;
}

// ---------- Site chrome and homepage ----------
const originalChrome=chrome;
chrome=function(){
 originalChrome();const nav=$('.nav');const first=$('a',nav);first.textContent='Home';first.insertAdjacentHTML('afterend','<a href="#menu">Menu</a><a href="#about">About Us</a><a href="#contact">Contact Us</a>');
 if(info)$('.site-footer').insertAdjacentHTML('beforeend',`<span class="zone-foot">All dates and times: ${esc(info.zoneLabel)}</span>`);
};
home=function(){
 main.innerHTML=`<div class="wrap"><section class="hero revised-hero" id="home"><div class="hero-copy"><div class="eyebrow">Reserve and Relax. Your Experience is on us.</div><h1>Great food<br><em>awaits you.</em></h1><a class="button" href="#reserve">Make a reservation now!</a><div class="hero-note"><span class="dot"></span>Open daily · ${timeLabel(info.settings.opening)} – ${timeLabel(info.settings.closing)} (${esc(zoneShort())})</div></div><div class="hero-menu-note"><span class="eyebrow">Three kitchens. One table.</span><h2>Filipino.<br>Chinese.<br>Japanese.</h2><a href="#menu">Explore our menu ↓</a></div></section>
 ${menuSection()}
 <section id="about" class="story"><div><div class="eyebrow">About us</div><h2>Three flavors.<br>One good experience.</h2></div><div><p>We believe that combining the three top delicacies—Filipino, Chinese, and Japanese flavors—in our dishes will motivate our guests to enjoy these foods every day.</p><p>We thoroughly craft every ingredient to serve our customers to the fullest.</p></div></section>
 <section id="contact" class="contact-section"><div class="eyebrow">Contact us</div><h2>We’re here to welcome you.</h2><div class="contact-grid"><iframe title="Map to Pamantasan ng Lungsod ng Maynila" loading="lazy" referrerpolicy="no-referrer-when-downgrade" src="https://www.google.com/maps?q=Pamantasan%20ng%20Lungsod%20ng%20Maynila%20General%20Luna%20Intramuros&output=embed"></iframe><div class="contact-details"><h3>Find us at PLM</h3><p>General Luna corner Muralla Street,<br>Intramuros, Manila, Philippines</p><p><a href="https://www.google.com/maps/search/?api=1&query=Pamantasan+ng+Lungsod+ng+Maynila" target="_blank" rel="noopener">Open directions in Google Maps ↗</a></p><p><strong>Contact:</strong> <a href="tel:+639123456788">${phoneDisplay('09123456788')}</a><br><strong>Email:</strong> <a href="mailto:inquiry@restorant.com">inquiry@restorant.com</a></p></div></div>
 <form id="inquiry-form" class="panel inquiry-form"><h3>Send an inquiry</h3><p>Leave a message for our restaurant team. Your inquiry will appear in the staff inbox.</p><div class="form-grid">${field('inquiryName','Name','text','required maxlength="120" autocomplete="name"')}${field('inquiryEmail','Email','email','required maxlength="254" autocomplete="email"')}${field('inquiryPhone','Mobile number','tel','required autocomplete="tel-national"')}<div class="field full"><label for="inquiryMessage">Comment or question</label><textarea id="inquiryMessage" required maxlength="3000" class="auto-grow"></textarea></div></div><p class="small">Your name and contact details are stored so authorized restaurant staff can respond to your inquiry.</p><button class="button">Send inquiry</button></form></section>
 <section class="visit"><div class="visit-copy"><h2>Whether it’s a date,<br>meetings, or reunions.</h2><p>We are happy to serve you.</p><a class="button" href="#reserve">Find your table ↗</a></div><div class="visit-info"><dl><dt>Opening hours</dt><dd>Monday – Sunday<br>${timeLabel(info.settings.opening)} – ${timeLabel(info.settings.closing)}<br><span class="small">${esc(info.zoneLabel)}</span></dd><dt>Your visit</dt><dd>Stay from ${stayRange()}.<br>25 tables · 100 guests maximum at one time.</dd><dt>Get in touch</dt><dd><a href="mailto:inquiry@restorant.com">inquiry@restorant.com</a><br><a href="tel:+639123456788">${phoneDisplay('09123456788')}</a></dd></dl></div></section></div>`;
 bindMenu(main);
 $('#inquiry-form').onsubmit=async e=>{e.preventDefault();const form=e.currentTarget,button=$('button',form);button.disabled=true;try{await api('/inquiries',{method:'POST',body:JSON.stringify({fullName:$('#inquiryName').value,email:$('#inquiryEmail').value,phoneNumber:toE164($('#inquiryPhone').value),message:$('#inquiryMessage').value})});form.reset();toast('Your inquiry has been saved for our team.');}catch(err){errorIn(form,err);}finally{button.disabled=false;}};
 const anchor=location.hash.slice(1);if(['menu','about','contact'].includes(anchor))requestAnimationFrame(()=>document.getElementById(anchor)?.scrollIntoView({behavior:'smooth'}));
};
const originalAuthPage=authPage;
authPage=function(register=false){originalAuthPage(register);$('.auth-aside').innerHTML='<h2>Your next gathering starts here.</h2><p>Resto Rant — Great food, Good experience</p>';const phone=$('label[for=phoneNumber]');if(phone)phone.textContent='Mobile number';};
const originalRender=render;
render=async function(){
 const anchor=location.hash.slice(1);
 if(['menu','about','contact'].includes(anchor)){++routeVersion;info=await api('/info');chrome();home();return;}
 if(anchor==='main'){$('#main').focus();return;}
 await originalRender();
 // The first chrome() of a visit runs before the server's clock information has arrived; add the timezone note once it has.
 if(info&&!$('.zone-foot'))chrome();
};
window.removeEventListener('hashchange',originalRender);window.addEventListener('hashchange',render);
document.addEventListener('input',e=>{if(e.target.matches('textarea')){e.target.style.height='auto';e.target.style.height=Math.min(e.target.scrollHeight,450)+'px';}});

// ---------- Booking ----------
reserve=async function(){
 if(!requireUser())return;if(user.role==='ADMIN'){main.innerHTML='<div class="wrap"><div class="empty"><h2>Restaurant staff</h2><p>Create reservations from a customer’s profile in the dashboard.</p><a href="#admin/customers">Open customers</a></div></div>';return;}
 const pricing=await loadPricing();
 // selected: the chosen start time. selection: exactly the table ids that are highlighted and that will be submitted.
 let selected='',selection=new Set(),assigned=false,tableRows=[],requestId=0,bookingRequestId=crypto.randomUUID();
 const loadedAt=performance.now();
 main.innerHTML=`<div class="wrap"><div class="page-head"><div class="eyebrow">Plan your gathering</div><h1>Your date. Your table.</h1><p>Choose a date, a seating time, and your tables. The price is the sum of the packages of the tables you reserve.</p><p class="zone-banner" role="note"><strong>Restaurant time now: <span id="restaurant-clock"></span></strong> · ${esc(info.zoneLabel)}<br>Every date and time on this page is the restaurant’s time, not your device’s.</p></div><div class="booking-layout"><section class="panel" id="booking-panel"><form id="booking-form"><div class="form-grid">${field('customerName','Your name','text',`readonly value="${esc(user.fullName)}"`)}${field('contactNumber','Mobile number','tel',`required value="${esc(user.phoneNumber)}" autocomplete="tel-national"`)}${field('date','1. Choose a date','date',`required min="${info.today}" max="${info.lastBookableDate||''}"`)}${field('guestCount','Number of guests','number',`required min="1" max="${info.settings.maxGuests}" step="1" value="2"`)}<div class="field"><label for="stayMinutes">How long will you stay?</label><select id="stayMinutes" name="stayMinutes">${stayOptions(info.settings.durationMinutes)}</select></div>
 <div class="full"><span class="section-label" id="time-label">2. Choose a seating time (${esc(zoneShort())})</span><div class="availability-key"><span class="available-key">Green: free for your whole stay</span><span class="reserved-key">Red: not available for your party and stay</span></div><div id="slots" class="slots" role="group" aria-labelledby="time-label"></div><p id="slot-note" role="status">Choose a date to see seating times.</p><div id="alternatives" class="suggestions" hidden></div></div>
 <div class="full" id="table-section" hidden><span class="section-label" id="table-label">3. Pick your tables</span><p class="small">Select enough tables to seat everyone, or let us choose the smallest suitable combination. Each table has its own package price, and your group has every selected table for the whole stay.</p><div class="row-actions"><button class="button secondary small" id="auto-tables" type="button">Assign tables for me</button><button class="text-button" id="clear-tables" type="button">Clear selection</button></div><div class="availability-key"><span class="selected-key">Solid green: selected for you</span><span class="available-key">Outlined: available</span><span class="reserved-key">Red: reserved</span></div><div id="seat-map" class="seat-map" role="group" aria-labelledby="table-label"></div><p id="seat-summary" class="seat-summary" role="status">No tables selected yet.</p></div>
 <div class="full price-box" id="price-box"><h3>Reservation package</h3><div id="price-lines"><p class="small">Select a time and tables to see the itemized price.</p></div></div>
 <fieldset class="full needs-field"><legend>How can we make your visit comfortable?</legend>${assistanceOptions.map(n=>`<label><input type="checkbox" name="need" value="${esc(n)}"> ${n}</label>`).join('')}<p class="small">Assistance and parking are requests, subject to staff confirmation.</p></fieldset><div class="field full"><label for="specialRequests">Anything we should know?</label><textarea id="specialRequests" maxlength="1000" placeholder="Dietary needs, allergies, restrictions, occasion details, or any other assistance…"></textarea></div>
 <div class="full deposit-policy"><h3>Payment and cancellation</h3><p>After you reserve, pay the total by GCash transfer to <strong>${esc(pricing.payment.gcashNumberDisplay)}</strong>${pricing.payment.accountName?` (${esc(pricing.payment.accountName)})`:''} and send us the reference number. Our staff verify every payment before a reservation is confirmed.</p><p>${esc(pricing.cancellation.summary)}</p><label><input id="policyAccepted" type="checkbox" required> I agree to the package price and the cancellation policy.</label></div></div>
 <button class="button" id="submit-booking" disabled>Reserve and continue to payment</button><p class="small">Your tables are held when you reserve. Payment is made afterwards in your own GCash app.</p></form></section>
 <aside class="booking-aside"><h3>Table packages</h3><ul class="package-list">${pricing.packages.filter(p=>p.tables>0).map(p=>`<li><span>Table for ${p.capacity} <span class="muted">(${plural(p.tables,'table')})</span></span><strong>${money(p.price)}</strong></li>`).join('')}</ul><p class="small">Reserving several tables adds their packages together.</p><div class="rule"></div><h3>Room for your people.</h3><p>100 guests maximum at any time. Choose a stay from ${stayRange()}; a time is offered only when your whole stay fits before we close at ${timeLabel(info.settings.closing)}.</p><div class="rule"></div><p>Plans changed? Cancel the reservation, or only some of its tables, online until ${info.settings.cancellationHours} hours before it starts.</p><p class="small">All times: ${esc(info.zoneLabel)}.</p></aside></div></div>`;
 // The clock is the server's restaurant time plus the time this page has been open; the device clock is not used.
 const tick=()=>{const el=$('#restaurant-clock');if(!el)return clearInterval(timer);const [h,m]=info.now.slice(11,16).split(':').map(Number);const t=(h*60+m+Math.floor((performance.now()-loadedAt)/60000))%1440;el.textContent=`${timeLabel(String(Math.floor(t/60)).padStart(2,'0')+':'+String(t%60).padStart(2,'0'))}, ${dateLabel(info.today)}`;};
 const timer=setInterval(tick,20000);tick();
 const guests=()=>Number($('#guestCount').value);
 const chosen=()=>tableRows.filter(t=>selection.has(t.id));
 const tablesUrl=()=>`/availability/tables?date=${$('#date').value}&time=${selected}&guests=${guests()}&duration=${$('#stayMinutes').value}`;
 function paintSeat(button){
  const table=tableRows.find(t=>t.id===Number(button.dataset.table)),on=selection.has(table.id);
  button.classList.toggle('selected',on);button.classList.toggle('assigned',on&&assigned);button.setAttribute('aria-pressed',String(on));
  const state=!table.available?'Reserved':on?(assigned?'Assigned for you':'Selected'):'Available';
  $('small',button).textContent=state;
  button.setAttribute('aria-label',`Table ${table.tableNumber}, ${table.capacity} seats, ${money(table.price)}, ${state.toLowerCase()}`);
 }
 function drawSeats(){
  $('#seat-map').innerHTML=tableRows.map(t=>`<button class="seat ${t.available?'available':'reserved'}" type="button" data-table="${t.id}" ${t.available?'':'disabled'} aria-pressed="false"><strong>${esc(t.tableNumber)}</strong><span>${t.capacity} seats</span><span class="seat-price">${money(t.price)}</span><small></small></button>`).join('');
  $('#seat-map').querySelectorAll('button').forEach(b=>{paintSeat(b);b.onclick=()=>{const id=Number(b.dataset.table);selection.has(id)?selection.delete(id):selection.add(id);if(assigned){assigned=false;$('#seat-map').querySelectorAll('button').forEach(paintSeat);}else paintSeat(b);update();};});
 }
 // One function keeps the highlighted tables, the seating summary, the price and the submit button in step.
 function update(){
  const tables=chosen(),seats=tables.reduce((s,t)=>s+t.capacity,0),total=tables.reduce((s,t)=>s+Number(t.price),0),need=guests();
  const names=tables.map(t=>t.tableNumber).join(', ');
  $('#seat-summary').textContent=!tables.length?'No tables selected yet. Pick tables or choose “Assign tables for me”.'
   :`${assigned?'Assigned for you':'Selected'}: ${names} · ${plural(seats,'seat')} for ${plural(need,'guest')}${seats<need?` · add ${plural(need-seats,'more seat')}`:''}`;
  $('#seat-summary').classList.toggle('short',tables.length>0&&seats<need);
  $('#price-lines').innerHTML=tables.length?`<table class="lines"><caption class="visually-hidden">Itemized reservation price</caption><thead><tr><th scope="col">Table</th><th scope="col">Seats</th><th scope="col">Package price</th></tr></thead><tbody>${tables.map(t=>`<tr><td>${esc(t.tableNumber)}</td><td>${t.capacity}</td><td>${money(t.price)}</td></tr>`).join('')}</tbody><tfoot><tr><th scope="row" colspan="2">Total for ${plural(tables.length,'table')}</th><td><strong>${money(total)}</strong></td></tr></tfoot></table><p class="small">${dateLabel($('#date').value)} · ${timeLabel(selected)} for ${stayLabel(Number($('#stayMinutes').value))} · ${plural(need,'guest')}. The final price is calculated by the restaurant when you reserve.</p>`:'<p class="small">Select a time and tables to see the itemized price.</p>';
  $('#submit-booking').disabled=!selected||!tables.length||seats<need;
  return total;
 }
 async function chooseTime(time){
  selected=time;selection.clear();assigned=false;
  $('#slots').querySelectorAll('button').forEach(b=>{b.classList.toggle('selected',b.dataset.time===time);b.setAttribute('aria-pressed',String(b.dataset.time===time));});
  $('#submit-booking').disabled=true;const thisRequest=requestId;const rows=await api(tablesUrl());if(thisRequest!==requestId||selected!==time)return;
  tableRows=rows;$('#table-section').hidden=false;drawSeats();update();
 }
 // "Assign tables for me": ask the server which tables it would choose, then select exactly those tables.
 async function assignTables(){
  if(!selected)return;const button=$('#auto-tables');button.disabled=true;
  try{const time=selected,thisRequest=requestId,rows=await api(tablesUrl());if(thisRequest!==requestId||selected!==time)return;
   tableRows=rows;selection=new Set(rows.filter(t=>t.suggested).map(t=>t.id));assigned=selection.size>0;drawSeats();update();
   if(!assigned)$('#seat-summary').textContent=`The free tables at this time cannot seat ${plural(guests(),'guest')}. Choose another time or a smaller party.`;
  }catch(e){toast(e.message);}finally{button.disabled=false;}
 }
 async function loadSlots(){
  const id=++requestId;selected='';selection.clear();assigned=false;tableRows=[];$('#slots').innerHTML='';$('#table-section').hidden=true;$('#alternatives').hidden=true;update();
  if(!$('#date').value){$('#slot-note').textContent='Choose a date to see seating times.';return;}
  const guestsValue=guests();if(!Number.isInteger(guestsValue)||guestsValue<1||guestsValue>info.settings.maxGuests){$('#slot-note').textContent=`Enter 1 to ${info.settings.maxGuests} guests.`;return;}
  $('#slot-note').textContent='Checking availability…';
  try{const date=$('#date').value,stay=$('#stayMinutes').value;const slots=await api(`/availability?date=${date}&guests=${guestsValue}&duration=${stay}`);if(id!==requestId||!$('#slots'))return;
   $('#slots').innerHTML=slots.map(s=>`<button type="button" class="slot ${s.availableTables?'available':'unavailable'}" data-time="${s.time}" ${s.availableTables?'':'disabled'} aria-pressed="false" aria-label="${timeLabel(s.time)} to ${timeLabel(s.endTime)}, ${s.availableTables?`${s.availableSeats} seats free`:'not available'}">${timeLabel(s.time)}<small>until ${timeLabel(s.endTime)}</small><small>${s.availableTables?`${s.availableSeats} seats free`:'Unavailable'}</small></button>`).join('');
   const open=slots.some(s=>s.availableTables);
   $('#slot-note').textContent=!slots.length?`No ${stayLabel(Number(stay))} stay can still start on this date before closing. Try a shorter stay or another date.`:open?`Each time shows when a ${stayLabel(Number(stay))} stay would end. Availability is checked again when you reserve.`:'No seating is available for this date, party size and length of stay.';
   $('#slots').querySelectorAll('button').forEach(b=>b.onclick=()=>chooseTime(b.dataset.time).catch(e=>toast(e.message)));
   if(!open){const dates=await api(`/availability/dates?date=${date}&guests=${guestsValue}&duration=${stay}`);if(id!==requestId)return;$('#alternatives').hidden=false;$('#alternatives').innerHTML=`<strong>Try another day</strong><p>${dates.length?'We found seats on these dates:':'No dates found in the next 30 days. Please contact us.'}</p>${dates.map(d=>`<button type="button" class="button secondary small" data-date="${d}">${dateLabel(d)}</button>`).join(' ')}`;$('#alternatives').querySelectorAll('button').forEach(b=>b.onclick=()=>{$('#date').value=b.dataset.date;loadSlots();});}
  }catch(e){if(id===requestId)$('#slot-note').textContent=e.message;}
 }
 // Changing the date, party size or length of stay recalculates availability and clears the table selection.
 $('#date').onchange=loadSlots;$('#guestCount').onchange=loadSlots;$('#stayMinutes').onchange=loadSlots;
 $('#auto-tables').onclick=assignTables;
 $('#clear-tables').onclick=()=>{selection.clear();assigned=false;$('#seat-map').querySelectorAll('button').forEach(paintSeat);update();};
 $('#booking-form').onsubmit=async e=>{
  e.preventDefault();const form=e.currentTarget,button=$('#submit-booking');const total=update();if(button.disabled)return;button.disabled=true;
  // tableIds are exactly the highlighted tables. expectedTotal only lets the server detect a price it did not show; it is never used as the price.
  const input={date:$('#date').value,time:selected,guestCount:guests(),contactNumber:toE164($('#contactNumber').value),specialRequests:$('#specialRequests').value,tableIds:[...selection],guestNeeds:[...form.querySelectorAll('[name=need]:checked')].map(x=>x.value),policyAccepted:$('#policyAccepted').checked,durationMinutes:Number($('#stayMinutes').value),expectedTotal:total,requestId:bookingRequestId};
  try{
   const r=await api('/reservations',{method:'POST',body:JSON.stringify(input)});
   const panel=$('#booking-panel');
   panel.innerHTML=`<div class="held"><div class="seal" aria-hidden="true">✓</div><h2>Your tables are held.</h2><p>${statusTag(r.status)} ${paymentTag(r)}</p><p>${dateLabel(r.startsAt)} · ${visitTime(r)} (${esc(zoneShort())})<br>${plural(r.guestCount,'guest')} · Table ${esc(r.tableNumber)}</p><span class="reference">${esc(r.referenceNumber)}</span>${itemLines(r)}${moneySummary(r)}<div id="pay-area"></div><p><a href="#reservations">Pay later from My reservations</a></p></div>`;
   paymentForm(r,$('#pay-area'),updated=>{$('#pay-area').innerHTML=`<div class="success" role="status"><strong>Payment reference received.</strong> It is pending verification: our staff will check the GCash account and mark it paid. Your reservation is not confirmed until then.</div><a class="button" href="#reservations">View my reservations</a>`;$('p',panel).innerHTML=`${statusTag(updated.status)} ${paymentTag(updated)}`;});
   panel.scrollIntoView({block:'start'});$('h2',panel).setAttribute('tabindex','-1');$('h2',panel).focus({preventScroll:true});
  }catch(err){errorIn(form,err);bookingRequestId=crypto.randomUUID();const time=selected;try{await chooseTime(time);}catch{await loadSlots();}}
 };
};

// ---------- Manual GCash payment ----------
/** Shows the transfer instructions and the form that submits a reference number (and optional receipt image) for staff to verify. */
async function paymentForm(r,host,after){
 const p=(pricingInfo||await loadPricing()).payment,rejected=r.payments.filter(x=>x.status==='REJECTED').at(-1);
 host.innerHTML=`<section class="pay-box" aria-labelledby="pay-title"><h3 id="pay-title">Pay ${money(r.money.due)} by GCash transfer</h3>${rejected?`<div class="error" role="alert">Your earlier reference ${esc(rejected.reference)} could not be verified${rejected.reviewNote?`: ${esc(rejected.reviewNote)}`:'.'} Please check the receipt and submit again.</div>`:''}<dl class="pay-target"><dt>Send to GCash number</dt><dd class="gcash-number">${esc(p.gcashNumberDisplay)}</dd>${p.accountName?`<dt>Account name</dt><dd>${esc(p.accountName)}</dd>`:''}<dt>Exact amount</dt><dd>${money(r.money.due)}</dd></dl><ol class="pay-steps">${p.steps.map(s=>`<li>${esc(s)}</li>`).join('')}</ol><p class="pay-warning"><strong>Keep your MPIN and OTP private.</strong> You pay inside your own GCash app. Resto Rant never asks for your MPIN or one-time PIN, here or by message.</p><form id="payment-form">${field('paymentReference','GCash reference number','text','required inputmode="numeric" autocomplete="off" minlength="8" maxlength="40" pattern="[A-Za-z0-9 \\-]{8,40}" placeholder="13-digit Ref. No. on the receipt"')}<div class="field"><label for="paymentProof">Receipt screenshot <span class="muted">(optional, PNG, JPEG or WebP, up to 3 MB)</span></label><input id="paymentProof" type="file" accept="image/png,image/jpeg,image/webp"></div><button class="button">Submit for verification</button><p class="small">Submitting a reference does not mark the reservation as paid. The payment stays pending until staff find the transfer in the restaurant’s GCash account.</p></form></section>`;
 $('#payment-form',host).onsubmit=async e=>{e.preventDefault();const form=e.currentTarget,button=$('button',form);const file=$('#paymentProof',form).files[0];
  if(file&&file.size>3*1024*1024){errorIn(form,new Error('The receipt image must be 3 MB or smaller.'));return;}
  button.disabled=true;try{const body=new FormData();body.append('reference',$('#paymentReference',form).value);if(file)body.append('proof',file);after(await api(`/reservations/${r.id}/payments`,{method:'POST',body}));}catch(err){errorIn(form,err);button.disabled=false;}};
}

// ---------- My reservations ----------
reservations=async function(version){
 if(!requireUser())return;if(user.role==='ADMIN'){location.hash='admin/reservations';return;}
 const data=await api('/reservations/my');if(version!==routeVersion)return;
 const upcoming=data.filter(r=>['PENDING','CONFIRMED'].includes(r.status)&&r.endsAt>info.now).sort((a,b)=>a.startsAt.localeCompare(b.startsAt));
 const history=data.filter(r=>!upcoming.includes(r));
 const month=r=>new Date(r.startsAt.slice(0,10)+'T12:00:00Z').toLocaleDateString('en',{month:'short',timeZone:'UTC'});
 function cards(rows){return rows.length?`<div class="reservation-list">${rows.map(r=>`<article class="reservation-card"><div class="date-block"><span>${month(r)}</span><strong>${Number(r.startsAt.slice(8,10))}</strong></div><div><h3>${visitTime(r)} · ${plural(r.guestCount,'guest')}</h3><p>${dateLabel(r.startsAt)} · ${esc(zoneShort())} · Table ${esc(r.tableNumber||'released')}</p><p>Ref. ${esc(r.referenceNumber.slice(0,8))} · ${Number(r.money.due)>0?`${money(r.money.due)} due`:`Total ${money(r.money.originalTotal)}`}${Number(r.money.refundDue)>0?` · ${money(r.money.refundDue)} refund owed to you`:''}</p></div><div class="card-actions">${statusTag(r.status)}${paymentTag(r)}<button class="text-button" data-detail="${r.id}">View details</button>${canPay(r)?`<button class="text-button" data-pay="${r.id}">Pay by GCash</button>`:''}${r.tablesCancellable?`<button class="text-button" data-cancel-tables="${r.id}">Cancel some tables</button>`:''}${r.cancellable?`<button class="text-button" data-cancel="${r.id}">Cancel reservation</button>`:''}</div></article>`).join('')}</div>`:'<div class="empty"><h3>No reservations here yet.</h3><p>Your visits will appear here.</p><a href="#reserve">Find your next table →</a></div>';}
 main.innerHTML=`<div class="wrap"><div class="page-head"><div class="eyebrow">Your moments, all in one place</div><h1>See you at the table.</h1><p>Keep track of your upcoming visits and past gatherings.</p><p class="zone-banner" role="note">All dates and times are ${esc(info.zoneLabel)}.</p></div><span class="section-label">Upcoming visits</span>${cards(upcoming)}<span class="section-label">Reservation history</span>${cards(history)}</div>`;
 const find=id=>data.find(r=>r.id===Number(id));
 main.querySelectorAll('[data-detail]').forEach(b=>b.onclick=()=>showDetails(find(b.dataset.detail),render));
 main.querySelectorAll('[data-pay]').forEach(b=>b.onclick=()=>payDialog(find(b.dataset.pay),render));
 main.querySelectorAll('[data-cancel-tables]').forEach(b=>b.onclick=()=>cancelTablesDialog(find(b.dataset.cancelTables),render));
 main.querySelectorAll('[data-cancel]').forEach(b=>b.onclick=()=>{const r=find(b.dataset.cancel);confirmAction(`Cancel this reservation? Every table will be released.${Number(r.money.paid)>0?' Your verified payment becomes refundable under the cancellation policy; staff send refunds by GCash.':''}`,async()=>{await api(`/reservations/${r.id}/cancel`,{method:'PUT'});toast('Your reservation has been cancelled.');await render();});});
};
function payDialog(r,refresh){
 $('#dialog-content').innerHTML=`<div class="eyebrow">Reservation ${esc(r.referenceNumber.slice(0,8))}</div><p>${dateLabel(r.startsAt)} · ${visitTime(r)} (${esc(zoneShort())})</p>${itemLines(r)}<div id="dialog-pay"></div>`;$('#dialog').showModal();
 paymentForm(r,$('#dialog-pay'),async()=>{$('#dialog').close();toast('Payment reference submitted. It is pending verification by our staff.');await refresh();});
}
showDetails=function(r,refresh=render){
 const staff=user.role==='ADMIN';
 $('#dialog-content').innerHTML=`<div class="eyebrow">Reservation details</div><h2>${esc(r.customerName)}</h2><p>${statusTag(r.status)} ${paymentTag(r)}</p><dl><dt>Reference</dt><dd>${esc(r.referenceNumber)}</dd><dt>Date</dt><dd>${dateLabel(r.startsAt)}</dd><dt>Time</dt><dd>${visitTime(r)} · ${stayLabel(r.durationMinutes)}<br><span class="small">${esc(info.zoneLabel)}</span></dd><dt>Party</dt><dd>${plural(r.guestCount,'guest')} · ${plural(r.seats,'seat')} at ${esc(r.tableNumber||'no tables')}</dd><dt>Contact</dt><dd>${esc(phoneDisplay(r.contactNumber))}</dd><dt>Requests</dt><dd>${esc(r.specialRequests||'No special requests')}</dd><dt>Assistance</dt><dd>${esc(r.guestNeeds||'None requested')}</dd></dl>
 <h3>Tables and price</h3>${itemLines(r)}${moneySummary(r)}
 <h3>Payments</h3>${r.payments.length?`<ul class="ledger">${r.payments.map(p=>`<li><strong>${money(p.amount)} · ${pretty(p.status)}</strong><span>Reference ${esc(p.reference)} · ${esc(methodLabel(p.method))}</span><span class="small">Submitted ${esc(instantLabel(p.submittedAt))}${p.reviewedAt?` · reviewed ${esc(instantLabel(p.reviewedAt))}${staff&&p.reviewedBy?` by ${esc(p.reviewedBy)}`:''}`:''}${p.reviewNote?` · ${esc(p.reviewNote)}`:''}</span></li>`).join('')}</ul>`:'<p class="small">No payment has been submitted.</p>'}
 ${r.refunds.length?`<h3>Refunds</h3><ul class="ledger">${r.refunds.map(f=>`<li><strong>${money(f.amount)} · ${({PENDING:'To be sent by staff',COMPLETED:'Sent',VOIDED:'Voided'})[f.status]}</strong><span>${esc(f.reason)}</span><span class="small">Recorded ${esc(instantLabel(f.createdAt))}${f.refundReference?` · GCash refund reference ${esc(f.refundReference)}`:''}${f.completedAt?` · ${esc(instantLabel(f.completedAt))}`:''}</span></li>`).join('')}</ul>`:''}
 ${staff?'<h3>History</h3><div id="audit-trail"><p class="small">Loading history…</p></div>':''}<div class="row-actions" id="detail-actions"></div>`;
 const actions=$('#detail-actions');
 if(!staff&&canPay(r)){actions.insertAdjacentHTML('beforeend','<button class="button" id="detail-pay">Pay by GCash</button>');$('#detail-pay').onclick=()=>payDialog(r,refresh);}
 if(staff?(['PENDING','CONFIRMED'].includes(r.status)&&activeItems(r).length>1):r.tablesCancellable){actions.insertAdjacentHTML('beforeend','<button class="button secondary" id="detail-tables">Cancel some tables</button>');$('#detail-tables').onclick=()=>cancelTablesDialog(r,refresh);}
 if(staff&&Number(r.money.maxRefundable)>0){actions.insertAdjacentHTML('beforeend','<button class="button secondary" id="detail-refund">Record a refund</button>');$('#detail-refund').onclick=()=>refundCreate(r,refresh);}
 if(!$('#dialog').open)$('#dialog').showModal();
 if(staff)api(`/admin/reservations/${r.id}/audit`).then(rows=>{const host=$('#audit-trail');if(host)host.innerHTML=rows.length?`<ol class="audit">${rows.map(a=>`<li><strong>${esc(pretty(a.action.replaceAll('_',' ')))}</strong><span>${esc(a.detail)}</span><span class="small">${esc(instantLabel(a.at))} · ${esc(a.actor)}</span></li>`).join('')}</ol>`:'<p class="small">No recorded actions (created before the audit history existed).</p>';}).catch(e=>{const host=$('#audit-trail');if(host)host.textContent=e.message;});
};
/** Cancel selected tables of a multi-table reservation; the rest stay booked. */
function cancelTablesDialog(r,refresh){
 const staff=user.role==='ADMIN',items=activeItems(r),paid=Number(r.money.paid)>0;
 $('#dialog-content').innerHTML=`<h2>Cancel some tables</h2><p>${dateLabel(r.startsAt)} · ${visitTime(r)} (${esc(zoneShort())}) · ${plural(r.guestCount,'guest')}</p><form id="cancel-tables-form"><fieldset class="needs-field"><legend>Tables to cancel (the others stay booked)</legend>${items.map(i=>`<label><input type="checkbox" name="table" value="${i.tableId}" data-seats="${i.capacity}" data-price="${i.price}"> Table ${esc(i.tableNumber)} · ${i.capacity} seats · ${money(i.price)}</label>`).join('')}</fieldset>${field('remainingGuests','Guests after this change','number',`required min="1" max="${r.guestCount}" step="1" value="${r.guestCount}"`)}<p id="cancel-summary" role="status" class="seat-summary">Choose at least one table. At least one table must remain.</p><p class="small">${esc((pricingInfo?.cancellation.summary)||`Tables cancelled at least ${info.settings.cancellationHours} hours before the reservation are refundable in full.`)} ${staff?'Refunds are sent manually by staff and recorded under Refunds.':'Refunds are sent by our staff through GCash.'}</p><button class="button">Cancel selected tables</button></form>`;
 if(!$('#dialog').open)$('#dialog').showModal();
 const form=$('#cancel-tables-form');
 const summary=()=>{const picked=[...form.querySelectorAll('[name=table]:checked')],left=items.length-picked.length,seats=items.reduce((s,i)=>s+i.capacity,0)-picked.reduce((s,x)=>s+Number(x.dataset.seats),0),value=picked.reduce((s,x)=>s+Number(x.dataset.price),0),guests=Number($('#remainingGuests').value);
  const problem=!picked.length?'Choose at least one table.':left<1?'At least one table must remain. To release every table, cancel the whole reservation.':guests>seats?`The remaining tables seat ${seats}. Reduce the guests to ${seats} or fewer.`:'';
  $('#cancel-summary').textContent=problem||`${plural(left,'table')} with ${plural(seats,'seat')} will stay booked for ${plural(guests,'guest')}. Package value of the cancelled tables: ${money(value)}${paid?(r.cancellable?', refundable under the policy.':', not refundable this close to the reservation.'):r.paymentStatus==='PENDING'?'. If staff verify the payment you submitted, this amount is refunded under the policy.':'; your amount due is reduced.'}`;
  $('#cancel-summary').classList.toggle('short',!!problem);$('button',form).disabled=!!problem;return picked;};
 form.oninput=summary;summary();
 form.onsubmit=async e=>{e.preventDefault();const picked=summary();if(!picked.length)return;try{await api(`${staff?'/admin':''}/reservations/${r.id}/cancel-tables`,{method:'POST',body:JSON.stringify({tableIds:picked.map(x=>Number(x.value)),guestCount:Number($('#remainingGuests').value)})});$('#dialog').close();toast('The selected tables were cancelled. The rest of the reservation is unchanged.');await refresh();}catch(err){errorIn(form,err);}};
}

// ---------- Staff: reservation list ----------
adminRows=function(data){return `<div class="table-wrap"><table><thead><tr><th scope="col">Guest / reference</th><th scope="col">Visit (${esc(zoneShort())})</th><th scope="col">Tables</th><th scope="col">Status</th><th scope="col">Payment</th><th scope="col">Manage</th></tr></thead><tbody>${data.length?data.map(r=>`<tr><td><strong>${esc(r.customerName)}</strong><span class="muted">${esc(r.referenceNumber.slice(0,8))} · ${plural(r.guestCount,'guest')}</span></td><td>${esc(r.startsAt.slice(0,10))}<br>${visitTime(r)}</td><td>${esc(r.tableNumber)}<br><span class="muted">${plural(r.seats,'seat')}</span></td><td>${statusTag(r.status)}</td><td>${paymentTag(r)}<br><span class="muted">${money(r.money.originalTotal)}${Number(r.money.refundDue)>0?` · ${money(r.money.refundDue)} refund owed`:''}</span></td><td><div class="row-actions"><button class="text-button" data-detail="${r.id}">Details</button>${r.status==='PENDING'&&['PAID','NOT_REQUIRED'].includes(r.paymentStatus)?`<button class="text-button" data-status="CONFIRMED" data-id="${r.id}">Confirm</button>`:''}${r.paymentStatus==='PENDING'?'<a class="text-button" href="#admin/payments">Verify payment</a>':''}${['PENDING','CONFIRMED'].includes(r.status)?`<button class="text-button" data-edit-booking="${r.id}">Edit</button>${activeItems(r).length>1?`<button class="text-button" data-cancel-tables="${r.id}">Cancel tables</button>`:''}<button class="text-button" data-status="CANCELLED" data-id="${r.id}">Cancel</button>`:''}${r.status==='CONFIRMED'&&r.endsAt<=info.now?`<button class="text-button" data-status="COMPLETED" data-id="${r.id}">Complete</button>`:''}${Number(r.money.refundDue)>0?`<button class="text-button" data-refund="${r.id}">Record refund</button>`:''}</div></td></tr>`).join(''):'<tr><td colspan="6">No reservations match these filters.</td></tr>'}</tbody></table></div>`;};
bindAdminRows=function(data,refresh){
 const find=id=>data.find(r=>r.id===Number(id));
 main.querySelectorAll('[data-detail]').forEach(b=>b.onclick=()=>showDetails(find(b.dataset.detail),refresh));
 main.querySelectorAll('[data-status]').forEach(b=>b.onclick=()=>confirmAction(`${({CONFIRMED:'Confirm',CANCELLED:'Cancel',COMPLETED:'Complete'})[b.dataset.status]} this reservation?${b.dataset.status==='CANCELLED'?' Every table is released and the cancellation policy decides what is refundable.':''}`,async()=>{await api(`/admin/reservations/${b.dataset.id}/status`,{method:'PUT',body:JSON.stringify({status:b.dataset.status})});toast('Reservation updated.');await refresh();}));
 main.querySelectorAll('[data-edit-booking]').forEach(b=>b.onclick=()=>staffBooking(find(b.dataset.editBooking),null,refresh));
 main.querySelectorAll('[data-cancel-tables]').forEach(b=>b.onclick=()=>cancelTablesDialog(find(b.dataset.cancelTables),refresh));
 main.querySelectorAll('[data-refund]').forEach(b=>b.onclick=()=>refundCreate(find(b.dataset.refund),refresh));
};
function staffBooking(r,customerId,refresh){$('#dialog-content').innerHTML=`<h2>${r?'Edit':'Create'} reservation</h2><p class="small">Times are ${esc(info.zoneLabel)}. Availability for the whole stay and the 100-person limit are rechecked on save. Leave table IDs blank for automatic assignment; the package price is calculated from the assigned tables.</p><form id="staff-booking">${field('staffDate','Date','date',`required min="${info.today}" max="${info.lastBookableDate||''}" value="${r?.startsAt.slice(0,10)||''}"`)}${field('staffTime','Time','time',`required step="1800" min="${info.settings.opening.slice(0,5)}" max="${info.settings.closing.slice(0,5)}" value="${r?.startsAt.slice(11,16)||'12:00'}"`)}${field('staffGuests','Guests','number',`required min="1" max="${info.settings.maxGuests}" step="1" value="${r?.guestCount||2}" ${r?'readonly':''}`)}<div class="field"><label for="staffStay">Length of stay</label><select id="staffStay">${stayOptions(r?r.durationMinutes:info.settings.durationMinutes)}</select></div>${field('staffPhone','Mobile number','tel',`required value="${esc(r?.contactNumber||'')}"`)}${field('staffTables','Table IDs (optional, comma separated)','text',`value="${r?.tableIds.join(',')||''}"`)}<div class="field"><label for="staffRequests">Requests / dietary needs</label><textarea id="staffRequests" maxlength="1000">${esc(r?.specialRequests||'')}</textarea></div><label><input type="checkbox" id="staffPolicy" required> Customer agrees to the package price and cancellation policy.</label><p class="small">${r?'To change the number of guests or release tables, use “Cancel tables”. Tables cannot be swapped once a payment is on file.':'The customer pays by GCash transfer from My reservations; you verify it under Payments.'}</p><button class="button">Save reservation</button></form>`;$('#dialog').showModal();$('#staff-booking').onsubmit=async e=>{e.preventDefault();const input={date:$('#staffDate').value,time:$('#staffTime').value,guestCount:Number($('#staffGuests').value),contactNumber:toE164($('#staffPhone').value),specialRequests:$('#staffRequests').value,tableIds:$('#staffTables').value.trim()?$('#staffTables').value.split(',').map(x=>Number(x.trim())):[],guestNeeds:r?.guestNeeds?r.guestNeeds.split(', '):[],policyAccepted:$('#staffPolicy').checked,durationMinutes:Number($('#staffStay').value)};try{const saved=await api(r?`/admin/reservations/${r.id}`:`/admin/customers/${customerId}/reservations`,{method:r?'PUT':'POST',body:JSON.stringify(input)});$('#dialog').close();await refresh();toast(`Reservation saved: ${saved.tableNumber}, ${money(saved.money.currentTotal)}.`);}catch(err){errorIn(e.currentTarget,err);}};}

// ---------- Staff: payments and refunds ----------
function paymentReview(p,refresh){
 const account=p.receivingAccount||pricingInfo?.payment.gcashNumber||'the restaurant GCash account';
 $('#dialog-content').innerHTML=`<h2>Verify payment</h2><dl><dt>Guest</dt><dd>${esc(p.customerName)}</dd><dt>Reservation</dt><dd>${esc(p.reservationReference)}</dd><dt>Amount claimed</dt><dd>${money(p.amount)}</dd><dt>GCash reference</dt><dd><strong>${esc(p.reference)}</strong></dd><dt>Sent to</dt><dd>${esc(account)}</dd><dt>Submitted</dt><dd>${esc(instantLabel(p.submittedAt))} by ${esc(p.submittedBy)}</dd></dl>${p.hasProof?`<p><a href="/api/admin/payments/${p.id}/proof" target="_blank" rel="noopener"><img class="proof" src="/api/admin/payments/${p.id}/proof" alt="Receipt image uploaded by the customer"></a><br><span class="small">A screenshot can be edited. Always check the account itself.</span></p>`:'<p class="small">No receipt image was attached.</p>'}<form id="review-form"><div class="pay-warning"><strong>Check the receiving account first.</strong> Open the transaction history of GCash ${esc(account)} and find an incoming transfer with this reference number and this exact amount. The reference number typed by the customer is not proof of payment.</div><label class="check"><input type="checkbox" id="verifiedAgainstAccount"> I found this transfer of ${money(p.amount)} with reference ${esc(p.reference)} in the receiving GCash account.</label>${field('paymentNote','Note (required when rejecting)','text','maxlength="500"')}<div class="row-actions"><button class="button" type="submit" value="approve">Mark as paid</button><button class="button secondary" type="submit" value="reject">Not found: reject</button></div></form>`;
 $('#dialog').showModal();
 $('#review-form').onsubmit=async e=>{e.preventDefault();const form=e.currentTarget,approved=e.submitter?.value!=='reject';
  if(approved&&!$('#verifiedAgainstAccount').checked){errorIn(form,new Error('Tick the box to confirm you found the transfer in the receiving GCash account.'));return;}
  if(!approved&&!$('#paymentNote').value.trim()){errorIn(form,new Error('Add a note explaining why the payment could not be verified.'));return;}
  try{await api(`/admin/payments/${p.id}/review`,{method:'PUT',body:JSON.stringify({approved,verifiedAgainstAccount:approved&&$('#verifiedAgainstAccount').checked,note:$('#paymentNote').value})});$('#dialog').close();await refresh();toast(approved?'Payment marked as paid.':'Payment rejected. The customer can submit again.');}catch(err){errorIn(form,err);}};
}
function refundCreate(r,refresh){
 const max=Number(r.money.maxRefundable),due=Number(r.money.refundDue),paid=r.payments.find(p=>p.status==='PAID');
 $('#dialog-content').innerHTML=`<h2>Record a refund</h2><p>${esc(r.customerName)} · ${esc(r.referenceNumber.slice(0,8))} · ${esc(phoneDisplay(r.contactNumber))}</p>${moneySummary(r)}<p class="pay-warning"><strong>This system does not send money.</strong> Recording a refund adds it to the list of refunds to send. You then send it yourself in GCash and enter the refund’s reference number.</p><form id="refund-form">${field('refundAmount','Refund amount (PHP)','number',`required min="0.01" max="${max}" step="0.01" value="${(due||max).toFixed(2)}"`)}<p class="small">Owed under the cancellation policy: ${money(due)}. Most that can still be refunded: ${money(max)}. Original payment reference: ${esc(paid?.reference||'none')}.</p>${field('refundReason','Reason','text','required maxlength="500"')}<button class="button">Add to refunds to send</button></form>`;
 if(!$('#dialog').open)$('#dialog').showModal();
 $('#refund-form').onsubmit=async e=>{e.preventDefault();try{await api(`/admin/reservations/${r.id}/refunds`,{method:'POST',body:JSON.stringify({amount:Number($('#refundAmount').value),reason:$('#refundReason').value})});$('#dialog').close();await refresh();toast('Refund recorded as pending. Send it in GCash, then mark it sent.');}catch(err){errorIn(e.currentTarget,err);}};
}
function refundClose(f,complete,refresh){
 $('#dialog-content').innerHTML=`<h2>${complete?'Mark refund as sent':'Void refund'}</h2><dl><dt>Guest</dt><dd>${esc(f.customerName)} · ${esc(phoneDisplay(f.customerPhone))}</dd><dt>Amount</dt><dd>${money(f.amount)}</dd><dt>Reason</dt><dd>${esc(f.reason)}</dd><dt>Payment reference</dt><dd>${esc(f.paymentReference)}</dd></dl><form id="refund-close">${complete?`<p class="pay-warning"><strong>Send the refund first.</strong> Transfer ${money(f.amount)} to the guest in GCash, then enter the reference number of that transfer. This only records what you did.</p>${field('refundReference','GCash refund reference number','text','required minlength="8" maxlength="40" autocomplete="off"')}${field('refundNote','Note (optional)','text','maxlength="500"')}`:`<p>Voiding means no money was sent for this record. The amount becomes refundable again.</p>${field('refundNote','Reason for voiding','text','required maxlength="500"')}`}<button class="button">${complete?'Record refund as sent':'Void refund'}</button></form>`;
 $('#dialog').showModal();
 $('#refund-close').onsubmit=async e=>{e.preventDefault();try{await api(`/admin/refunds/${f.id}/${complete?'complete':'void'}`,{method:'PUT',body:JSON.stringify(complete?{refundReference:$('#refundReference').value,note:$('#refundNote').value}:{note:$('#refundNote').value})});$('#dialog').close();await refresh();toast(complete?'Refund recorded as sent.':'Refund voided.');}catch(err){errorIn(e.currentTarget,err);}};
}

const originalAdmin=admin;
admin=async function(section,version){
 if(!requireUser(true))return;
 if(!pricingInfo)await loadPricing();
 const live=()=>version===routeVersion;
 if(section==='payments'){
  const content=adminShell(section);
  const load=async()=>{const rows=await api('/admin/payments');if(!live())return;const waiting=rows.filter(p=>p.status==='PENDING').reverse();
   const table=(list,empty,review)=>`<div class="table-wrap"><table><thead><tr><th scope="col">Submitted</th><th scope="col">Guest / reservation</th><th scope="col">Amount</th><th scope="col">GCash reference</th><th scope="col">Status</th><th scope="col">${review?'Action':'Reviewed'}</th></tr></thead><tbody>${list.length?list.map(p=>`<tr><td>${esc(instantLabel(p.submittedAt))}</td><td><strong>${esc(p.customerName)}</strong><span class="muted">${esc(p.reservationReference.slice(0,8))}</span></td><td>${money(p.amount)}</td><td>${esc(p.reference)}${p.hasProof?' <span class="muted">· receipt attached</span>':''}<br><span class="muted">${esc(methodLabel(p.method))}</span></td><td><span class="status pay-${p.status.toLowerCase()}">${pretty(p.status)}</span></td><td>${review?`<button class="text-button" data-review="${p.id}">Verify</button>`:`${esc(p.reviewedBy||'')}${p.reviewedAt?`<br><span class="muted">${esc(instantLabel(p.reviewedAt))}</span>`:''}${p.reviewNote?`<br><span class="muted">${esc(p.reviewNote)}</span>`:''}`}</td></tr>`).join(''):`<tr><td colspan="6">${empty}</td></tr>`}</tbody></table></div>`;
   content.innerHTML=`<h1>Payments</h1><p>Guests pay by GCash transfer to <strong>${esc(pricingInfo.payment.gcashNumberDisplay)}</strong> and submit the reference number. Nothing is confirmed automatically: a payment is marked paid only after you find it in that account’s transaction history.</p><h2>Waiting for verification (${waiting.length})</h2>${table(waiting,'No payments are waiting.',true)}<h2>Reviewed payments</h2>${table(rows.filter(p=>p.status!=='PENDING'),'No reviewed payments yet.',false)}<p class="small">Times are ${esc(info.zoneLabel)}.</p>`;
   content.querySelectorAll('[data-review]').forEach(b=>b.onclick=()=>paymentReview(rows.find(p=>p.id===Number(b.dataset.review)),load));};
  await load();return;
 }
 if(section==='refunds'){
  const content=adminShell(section);
  const load=async()=>{const [due,rows]=await Promise.all([api('/admin/refunds/due'),api('/admin/refunds')]);if(!live())return;
   content.innerHTML=`<h1>Refunds</h1><p>Refunds are sent by hand from the restaurant’s GCash account and tracked here. This page never sends money: “pending” means still to be sent, “completed” means a staff member recorded the GCash reference of the refund they sent.</p><h2>Refunds owed (${due.length})</h2><div class="table-wrap"><table><thead><tr><th scope="col">Guest</th><th scope="col">Reservation</th><th scope="col">Paid</th><th scope="col">Owed under policy</th><th scope="col">Action</th></tr></thead><tbody>${due.length?due.map(r=>`<tr><td><strong>${esc(r.customerName)}</strong><span class="muted">${esc(phoneDisplay(r.contactNumber))}</span></td><td>${esc(r.referenceNumber.slice(0,8))}<br><span class="muted">${esc(r.startsAt.slice(0,10))} · ${pretty(r.status)}</span></td><td>${money(r.money.paid)}</td><td><strong>${money(r.money.refundDue)}</strong></td><td><div class="row-actions"><button class="text-button" data-detail="${r.id}">Details</button><button class="text-button" data-refund="${r.id}">Record refund</button></div></td></tr>`).join(''):'<tr><td colspan="5">No refunds are owed.</td></tr>'}</tbody></table></div><h2>Refund records</h2><div class="table-wrap"><table><thead><tr><th scope="col">Recorded</th><th scope="col">Guest</th><th scope="col">Amount / reason</th><th scope="col">Payment ref.</th><th scope="col">Refund ref.</th><th scope="col">Status</th><th scope="col">Action</th></tr></thead><tbody>${rows.length?rows.map(f=>`<tr><td>${esc(instantLabel(f.createdAt))}<br><span class="muted">${esc(f.createdBy)}</span></td><td><strong>${esc(f.customerName)}</strong><span class="muted">${esc(f.reservationReference.slice(0,8))}</span></td><td>${money(f.amount)}<br><span class="muted">${esc(f.reason)}</span></td><td>${esc(f.paymentReference)}</td><td>${esc(f.refundReference||'Not sent yet')}${f.completedAt?`<br><span class="muted">${esc(instantLabel(f.completedAt))} · ${esc(f.completedBy||'')}</span>`:''}${f.note?`<br><span class="muted">${esc(f.note)}</span>`:''}</td><td><span class="status refund-${f.status.toLowerCase()}">${({PENDING:'To send',COMPLETED:'Sent',VOIDED:'Voided'})[f.status]}</span></td><td>${f.status==='PENDING'?`<div class="row-actions"><button class="text-button" data-sent="${f.id}">Mark sent</button><button class="text-button" data-void="${f.id}">Void</button></div>`:''}</td></tr>`).join(''):'<tr><td colspan="7">No refunds have been recorded.</td></tr>'}</tbody></table></div><p class="small">Times are ${esc(info.zoneLabel)}.</p>`;
   content.querySelectorAll('[data-detail]').forEach(b=>b.onclick=()=>showDetails(due.find(r=>r.id===Number(b.dataset.detail)),load));
   content.querySelectorAll('[data-refund]').forEach(b=>b.onclick=()=>refundCreate(due.find(r=>r.id===Number(b.dataset.refund)),load));
   content.querySelectorAll('[data-sent]').forEach(b=>b.onclick=()=>refundClose(rows.find(f=>f.id===Number(b.dataset.sent)),true,load));
   content.querySelectorAll('[data-void]').forEach(b=>b.onclick=()=>refundClose(rows.find(f=>f.id===Number(b.dataset.void)),false,load));};
  await load();return;
 }
 if(section==='pricing'){
  const content=adminShell(section);
  const load=async()=>{const p=await loadPricing();if(!live())return;
   content.innerHTML=`<h1>Package prices</h1><p>Each table has a reservation package price set by its number of seats. A reservation costs the sum of the packages of its tables.</p><div class="table-wrap"><table><thead><tr><th scope="col">Table size</th><th scope="col">Tables in the dining room</th><th scope="col">Package price (PHP)</th><th scope="col">Save</th></tr></thead><tbody>${p.packages.map(k=>`<tr><td><strong>${k.capacity} seats</strong>${k.configured?'':'<span class="muted">No price set: using a default based on the nearest smaller table</span>'}</td><td>${k.tables}</td><td><form class="price-form" data-capacity="${k.capacity}" id="price-${k.capacity}"><label class="visually-hidden" for="price-input-${k.capacity}">Package price for ${k.capacity}-seat tables</label><input id="price-input-${k.capacity}" type="number" required min="0.01" max="1000000" step="0.01" value="${Number(k.price).toFixed(2)}"></form></td><td><button class="button small" form="price-${k.capacity}">Save</button></td></tr>`).join('')}</tbody></table></div><p class="small">New prices apply to reservations made from now on. Existing reservations keep the prices they were booked with, and every change is recorded in the audit history.</p>`;
   content.querySelectorAll('.price-form').forEach(form=>form.onsubmit=async e=>{e.preventDefault();try{await api(`/admin/pricing/${form.dataset.capacity}`,{method:'PUT',body:JSON.stringify({price:Number($('input',form).value)})});toast(`Package price for ${form.dataset.capacity}-seat tables saved.`);await load();}catch(err){toast(err.message);}});};
  await load();return;
 }
 if(section==='accounts'){const content=adminShell(section);const staff=await api('/admin/accounts');if(!live())return;content.innerHTML=`<h1>Staff accounts</h1><p>Create a new administrator. This action is available only to signed-in administrators.</p><button class="button" id="new-admin">Register administrator</button><span class="section-label">Registered staff (${staff.length})</span><div class="table-wrap"><table><thead><tr><th scope="col">Name</th><th scope="col">Email</th><th scope="col">Mobile</th><th scope="col">Status</th></tr></thead><tbody>${staff.length?staff.map(s=>`<tr><td><strong>${esc(s.fullName)}</strong>${s.email===user.email?' <span class="muted">(you)</span>':''}</td><td>${esc(s.email)}</td><td>${esc(phoneDisplay(s.phoneNumber))}</td><td><span class="status ${s.enabled?'confirmed':'cancelled'}">${s.enabled?'Active':'Disabled'}</span></td></tr>`).join(''):'<tr><td colspan="4">No staff accounts yet.</td></tr>'}</tbody></table></div><p class="small">Newest accounts are listed first.</p>`;$('#new-admin').onclick=()=>accountForm(true);return;}
 if(section==='inquiries'){const content=adminShell(section);const rows=await api('/admin/inquiries');if(!live())return;content.innerHTML=`<h1>Guest inquiries</h1><p>Messages submitted through Contact Us. Reply using the guest’s contact details.</p>${rows.map(r=>`<article class="panel inquiry-card"><h3>${esc(r.fullName)}</h3><p>${esc(r.email)} · ${esc(phoneDisplay(r.phoneNumber))}</p><p class="inquiry-message">${esc(r.message)}</p><p class="small">${esc(instantLabel(r.createdAt))} (${esc(zoneShort())}) · ${r.resolved?'Resolved':'Open'}</p>${r.resolved?'':`<button class="button small" data-resolve="${r.id}">Mark resolved</button>`}</article>`).join('')||'<p>No inquiries yet.</p>'}`;content.querySelectorAll('[data-resolve]').forEach(b=>b.onclick=async()=>{try{await api(`/admin/inquiries/${b.dataset.resolve}/resolve`,{method:'PUT'});await render();}catch(e){toast(e.message);}});return;}
 if(section==='deposit'){location.hash='admin/pricing';return;}
 await originalAdmin(section,version);if(!live())return;
 if(section==='tables'){$('#admin-content').insertAdjacentHTML('afterbegin','<button class="button secondary small" id="balanced-layout">Apply 25-table / 100-seat layout</button>');$('#balanced-layout').onclick=()=>confirmAction('Replace the enabled dining-room layout with 8 tables for 2, 9 for 4, and 8 for 6? Old tables and history are retained. Future reservations must first be finished or cancelled.',async()=>{await api('/admin/layout',{method:'POST'});await render();});}
 if(section==='customers'){$('#admin-content').insertAdjacentHTML('afterbegin','<button class="button secondary small" id="new-customer">Create customer</button>');$('#new-customer').onclick=()=>accountForm(false);const enhance=()=>{main.querySelectorAll('[data-history]').forEach(b=>{if(b.dataset.enhanced)return;b.dataset.enhanced='yes';const id=b.dataset.history;const host=b.parentElement;host.insertAdjacentHTML('beforeend',` <button class="text-button" data-profile="${id}">Edit</button> <button class="text-button" data-book-for="${id}">Book</button>`);host.querySelector('[data-profile]').onclick=()=>profileForm(id);host.querySelector('[data-book-for]').onclick=()=>staffBooking(null,id,render);});};enhance();const observer=new MutationObserver(()=>{if(!$('#customers')){observer.disconnect();return;}enhance();});observer.observe($('#customers'),{childList:true,subtree:true});}
};
function accountForm(isAdmin){$('#dialog-content').innerHTML=`<h2>${isAdmin?'Register administrator':'Create customer'}</h2><form id="account-form">${field('fullName','Full name','text','required maxlength="120"')}${field('email','Email','email','required maxlength="254"')}${field('phoneNumber','Mobile number','tel','required')}${field('password','Password','password','required minlength="10" maxlength="72" autocomplete="new-password"')}${field('confirmPassword','Confirm password','password','required minlength="10" maxlength="72" autocomplete="new-password"')}<button class="button">Create account</button></form>`;$('#dialog').showModal();$('#account-form').onsubmit=async e=>{e.preventDefault();try{const data=Object.fromEntries(new FormData(e.currentTarget));data.phoneNumber=toE164(data.phoneNumber);await api(isAdmin?'/admin/accounts':'/admin/customers',{method:'POST',body:JSON.stringify(data)});$('#dialog').close();toast('Account created.');await render();}catch(err){errorIn(e.currentTarget,err);}};}
async function profileForm(id){try{const rows=await api('/admin/customers');const c=rows.find(r=>r.id===Number(id));$('#dialog-content').innerHTML=`<h2>Edit customer</h2><p>${esc(c.email)}</p><form id="profile-form">${field('profileName','Full name','text',`required maxlength="120" value="${esc(c.fullName)}"`)}${field('profilePhone','Mobile number','tel',`required value="${esc(c.phoneNumber)}"`)}${phoneValid(c.phoneNumber)?'':`<p class="small">Stored value “${esc(c.phoneNumber)}” is not a Philippine mobile number. Enter the correct number to save.</p>`}<div class="field"><label for="profileEnabled">Account access</label><select id="profileEnabled"><option value="true">Enabled</option><option value="false" ${!c.enabled?'selected':''}>Disabled</option></select></div><p class="small">Disabling blocks login and new customer actions while preserving reservation history.</p><button class="button">Save customer</button></form>`;$('#dialog').showModal();$('#profile-form').onsubmit=async e=>{e.preventDefault();try{await api(`/admin/customers/${id}`,{method:'PUT',body:JSON.stringify({fullName:$('#profileName').value,phoneNumber:toE164($('#profilePhone').value),enabled:$('#profileEnabled').value==='true'})});$('#dialog').close();await render();}catch(err){errorIn(e.currentTarget,err);}};}catch(e){toast(e.message);}}

// ---------- Form helpers applied to every page and dialog: password preview, +63 phone fields, number/date limits ----------
let phoneHintId=0;
function enhanceInputs(root){
 root.querySelectorAll('input[type=password]:not([data-toggle])').forEach(input=>{
  input.dataset.toggle='yes';const wrap=document.createElement('span');wrap.className='password-wrap';input.replaceWith(wrap);wrap.append(input);
  const button=document.createElement('button');button.type='button';button.className='password-toggle';button.textContent='Show';button.setAttribute('aria-label','Show password');button.setAttribute('aria-pressed','false');
  button.onclick=()=>{const show=input.type==='password';input.type=show?'text':'password';button.textContent=show?'Hide':'Show';button.setAttribute('aria-label',show?'Hide password':'Show password');button.setAttribute('aria-pressed',String(show));input.focus();};
  wrap.append(button);
 });
 // Mobile number fields: a faint, fixed +63 prefix and room for the 10 digits that follow it.
 // A stored 09XXXXXXXXX or +639XXXXXXXXX value is shown as those same 10 digits.
 root.querySelectorAll('input[type=tel]:not([data-ph])').forEach(input=>{
  input.dataset.ph='yes';const wrap=document.createElement('span');wrap.className='phone-wrap';input.replaceWith(wrap);
  const prefix=document.createElement('span');prefix.className='phone-prefix';prefix.textContent='+63';prefix.setAttribute('aria-hidden','true');
  const hint=document.createElement('span');hint.className='visually-hidden';hint.id='phone-hint-'+(++phoneHintId);hint.textContent='Philippine mobile number. Enter the 10 digits after +63, starting with 9.';
  wrap.append(prefix,input,hint);
  input.value=phoneDigits(input.getAttribute('value')??input.value);
  input.setAttribute('inputmode','numeric');input.setAttribute('pattern','9[0-9]{9}');input.setAttribute('maxlength','16');input.removeAttribute('minlength');
  input.setAttribute('placeholder','9171234567');input.setAttribute('title','Enter the 10 digits after +63, starting with 9 (for example 9171234567)');input.setAttribute('aria-describedby',hint.id);
 });
 root.querySelectorAll('input[type=number]:not([data-limited])').forEach(input=>{input.dataset.limited='yes';if(!input.step||Number.isInteger(Number(input.step)))input.setAttribute('inputmode','numeric');});
}
const integerInput=input=>input.matches('input[type=number]')&&(!input.step||(input.step!=='any'&&Number.isInteger(Number(input.step))));
function clampInput(input){
 if(input.matches('input[type=number]')&&input.value!==''){let v=Number(input.value);if(Number.isNaN(v))return;if(integerInput(input))v=Math.round(v);if(input.min!=='')v=Math.max(v,Number(input.min));if(input.max!=='')v=Math.min(v,Number(input.max));if(String(v)!==input.value)input.value=String(v);}
 if(input.matches('input[type=date]')&&input.value){if(input.min&&input.value<input.min)input.value=input.min;if(input.max&&input.value>input.max)input.value=input.max;}
}
document.addEventListener('keydown',e=>{if(integerInput(e.target)&&['e','E','+','-','.',','].includes(e.key))e.preventDefault();});
// Typing or pasting 0917…, 63917… or +63 917 … leaves just the 10 digits after +63 in the field.
document.addEventListener('input',e=>{const t=e.target;if(t.matches('input[type=tel]')){const cleaned=phoneDigits(t.value);if(cleaned!==t.value)t.value=cleaned;}
 if(t.matches('input[type=number]')&&t.max!==''&&Number(t.value)>Number(t.max))t.value=t.max;});
// Capture phase so values are corrected before the page's own change handlers run.
document.addEventListener('change',e=>clampInput(e.target),true);
document.addEventListener('focusout',e=>clampInput(e.target));
new MutationObserver(()=>enhanceInputs(document)).observe(document.body,{childList:true,subtree:true});
enhanceInputs(document);
